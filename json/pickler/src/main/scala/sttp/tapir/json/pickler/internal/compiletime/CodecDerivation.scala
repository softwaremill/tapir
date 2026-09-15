package sttp.tapir.json.pickler.internal.compiletime

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.{CodecMakerConfig, JsonCodecMaker}
import hearth.MacroCommons
import hearth.fp.effect.*
import hearth.std.*
import sttp.tapir.json.pickler.PicklerConfiguration
import sttp.tapir.json.pickler.internal.runtime.{CodecCombinators, LeafCodecs}

/** Derivation of the `JsonValueCodec` half of a `Pickler`, by **configuring `JsonCodecMaker.make`** rather than by generating reader/writer
  * code ourselves.
  *
  * ==Shape of the generated code==
  * {{{
  * {
  *   implicit lazy val codec$Address: JsonValueCodec[Address] = JsonCodecMaker.make[Address](<config for Address>)
  *   implicit lazy val codec$Status:  JsonValueCodec[Status]  = JsonCodecMaker.make[Status](<config for Status>)
  *   JsonCodecMaker.make[Person](<config for Person>)
  * }
  * }}}
  * One `make` per case class and per sealed hierarchy in the type graph, each with its own configuration. jsoniter finds the sibling codecs
  * through implicit search when it meets a nested type, which is what allows every configuration to be *local*: `fieldNameMapper` is keyed
  * by bare field name, so a graph-wide mapper could not express `@encodedName` on one class's `name` but not another's.
  *
  * ==Why the configuration is computed here==
  * jsoniter interprets its `CodecMakerConfig` argument at *its* expansion time, and only accepts trees made of literals and stable
  * references. tapir's `PicklerConfiguration` is a runtime value, so it is folded with `semiEval` first, its `toEncodedName` /
  * `toDiscriminatorValue` are *invoked* during our expansion ([[NameSupport]]), and the results are emitted as literal `Match` cases (see
  * [[PlatformSupport]]). The schema half splices the very same literals, so the two agree by construction.
  *
  * ==Knobs, and why each is set==
  *   - `transientEmpty(false)`: empty collections are written as `[]`.
  *   - `transientDefault(false)`: fields equal to their Scala default are still written.
  *   - `transientNone`: from `PicklerConfiguration.transientNone`.
  *   - `requireDiscriminatorFirst(false)`: the discriminator may appear anywhere in the object.
  *   - `allowRecursiveTypes(true)`: recursion is handled by jsoniter's own `def`s; the schema uses `SRef`.
  *   - `alwaysEmitDiscriminator(true)` on case classes: a leaf written on its own carries its discriminator, so that a member of a sealed
  *     hierarchy is encoded the same way whether it is written through the parent or through its own pickler. It is also what makes
  *     per-leaf codecs composable into the hierarchy codec: jsoniter delegates to the leaf's implicit and the leaf writes the tag itself.
  *   - `discriminatorFieldName(None)` on a hierarchy whose leaves are all singletons: such hierarchies are encoded as bare strings.
  *
  * ==`Either`==
  * jsoniter has no encoding for it, so an `Either[L, R]` in the graph gets a hand-written `CodecCombinators.either` val over the codecs of
  * its two sides (untagged, as tapir core's `Codec.eitherRight`). A side that has a val of its own is referenced; any other (a primitive, a
  * collection) gets an inline `make` under the base config.
  *
  * ==What is deliberately not supported==
  * tapir's `@default` annotation does not drive decoding: jsoniter fills a missing field only from a Scala default parameter and has no
  * hook for anything else. Scala default parameters *are* honoured.
  */
trait CodecDerivation {
  this: MacroCommons & StdExtensions & TypeShape & AnnotationSupport & NameSupport & PlatformSupport & ImplicitPicklerSupport =>

  private[compiletime] object CTypes {
    def CodecOf[A: Type]: Type[JsonValueCodec[A]] = Type.of[JsonValueCodec[A]]
    lazy val MakerConfig: Type[CodecMakerConfig] = Type.of[CodecMakerConfig]
    lazy val StringT: Type[String] = Type.of[String]
    lazy val BooleanT: Type[Boolean] = Type.of[Boolean]
    lazy val OptionStringT: Type[Option[String]] = Type.of[Option[String]]
    lazy val StringPF: Type[PartialFunction[String, String]] = Type.of[PartialFunction[String, String]]
    lazy val StringFn: Type[String => String] = Type.of[String => String]
    lazy val JBigDecimalT: Type[java.math.BigDecimal] = Type.of[java.math.BigDecimal]
    lazy val JBigIntegerT: Type[java.math.BigInteger] = Type.of[java.math.BigInteger]
  }

  /** References to the sibling vals of the generated block, by the `plainPrint` of the type they are a codec for. */
  private type CodecRefs = String => Option[UntypedExpr]

  /** One `implicit lazy val` of type `JsonValueCodec[tpe]` the generated code will contain: a `JsonCodecMaker.make` call, a hand-written
    * leaf codec from [[LeafCodecs]], a user pickler's codec, or a [[CodecCombinators]] call over sibling vals (hence the right-hand side is
    * a function of the block's references).
    */
  private final case class CodecVal(tpe: ??, rhs: CodecRefs => UntypedExpr)
  private object CodecVal {
    def const(tpe: ??, rhs: UntypedExpr): CodecVal = CodecVal(tpe, _ => rhs)
  }

  // -----------------------------------------------------------------------------------------------------------------
  // Entry point
  // -----------------------------------------------------------------------------------------------------------------

  def deriveCodec[A: Type](config: PicklerConfiguration): MIO[Expr[JsonValueCodec[A]]] =
    Log.namedScope(s"deriveCodec[${Type[A].prettyPrint}]") {
      implicit val CodecA: Type[JsonValueCodec[A]] = CTypes.CodecOf[A]
      val rootShape = classify[A]
      for {
        units <- walkChildren(childrenOf(rootShape), config, Set(Type[A].plainPrint), Vector.empty).map(_._2.toList)
        // The root gets an implicit too. `make[A]` itself never looks its own type up (jsoniter pre-seeds the root as
        // "no implicit"), but a *nested* `make[Leaf]` whose field refers back to `A` must find it: otherwise jsoniter
        // would inline `A` there, under the leaf's configuration -- whose leaf-name mapper knows only that one leaf.
        rootUnits <- codecValFor[A](rootShape, config, isRoot = true).map {
          case Nil   => List(CodecVal.const(Type[A].as_??, makeExpr[A](baseConfig(config)).asUntyped))
          case units => units
        }
        _ <- Log.info(s"Emitting ${units.size} nested codec(s): ${units.map(_.tpe.Underlying.plainPrint).mkString(", ")}")
      } yield {
        // The last unit is the root's own codec, whatever else its shape needed emitted before it.
        val all = units ++ rootUnits
        val keys = all.map(_.tpe.Underlying.plainPrint)
        def refsByType(refs: List[UntypedExpr]): CodecRefs = key =>
          keys.indexOf(key) match {
            case -1 => None
            case i  => Some(refs(i))
          }
        val vals = all.zipWithIndex.map { case (unit, i) =>
          import unit.tpe.Underlying as U
          implicit val CodecU: Type[JsonValueCodec[U]] = CTypes.CodecOf[U]
          (s"codec$$${Type[U].shortName}$$$i", Type[JsonValueCodec[U]].asUntyped, (refs: List[UntypedExpr]) => unit.rhs(refsByType(refs)))
        }
        implicitLazyVals[JsonValueCodec[A]](vals)(refs => refs.last.asTyped[JsonValueCodec[A]])
      }
    }

  private def makeExpr[A: Type](config: Expr[CodecMakerConfig]): Expr[JsonValueCodec[A]] = {
    implicit val CodecA: Type[JsonValueCodec[A]] = CTypes.CodecOf[A]
    implicit val ConfigT: Type[CodecMakerConfig] = CTypes.MakerConfig
    Expr.quote(JsonCodecMaker.make[A](Expr.splice(config)))
  }

  // -----------------------------------------------------------------------------------------------------------------
  // Type graph walk
  // -----------------------------------------------------------------------------------------------------------------

  /** The state threaded through the walk over every type reachable from the root (dependencies first): the types already visited, and the
    * vals emitted so far.
    */
  private type Walk = (Set[String], Vector[CodecVal])

  private def makeUnit[A: Type](config: Expr[CodecMakerConfig]): CodecVal = CodecVal.const(Type[A].as_??, makeExpr[A](config).asUntyped)

  /** The vals `A` contributes to the block given its shape (usually one, for `A` itself; empty for the shapes jsoniter derives on its own).
    * Shared by the root and the nested walk; for the root, the last val must be `A`'s own codec.
    */
  private def codecValFor[A: Type](shape: Shape[A], config: PicklerConfiguration, isRoot: Boolean): MIO[List[CodecVal]] = shape match {
    case Shape.JavaBigDecimal()       => MIO.pure(List(javaBigDecimalVal[A]))
    case Shape.JavaBigInteger()       => MIO.pure(List(javaBigIntegerVal[A]))
    case Shape.Product(_, params)     => productConfig[A](params, config).map(cfg => List(makeUnit[A](cfg)))
    case Shape.Enumeration(_, leaves) => hierarchyConfig[A](leaves, asEnumeration = true, config).map(cfg => List(makeUnit[A](cfg)))
    case Shape.Coproduct(_, leaves)   => hierarchyConfig[A](leaves, asEnumeration = false, config).map(cfg => List(makeUnit[A](cfg)))
    case Shape.EitherOf(l, r)         => MIO.pure(List(eitherUnit[A](l, r, config)))
    case Shape.NestedOption(i, e)     => MIO.pure(nestedOptionUnits[A](i, e, config, isRoot))
    case Shape.Tuple()                => fail(PicklerDerivationError.TupleNotSupported(Type[A].plainPrint))
    case Shape.NonStringMap(key, _)   => fail(PicklerDerivationError.NonStringMapKey(key.Underlying.plainPrint))
    case _                            => MIO.pure(Nil)
  }

  private def fail[T](error: PicklerDerivationError): MIO[T] = Log.error(error.message) >> MIO.fail(error)

  // Hand-written codecs from `LeafCodecs`, for leaf types tapir has a `Schema` for but `JsonCodecMaker` cannot derive.

  private def javaBigDecimalVal[A: Type]: CodecVal = {
    implicit val JBigDecimalT: Type[java.math.BigDecimal] = CTypes.JBigDecimalT
    implicit val CodecBD: Type[JsonValueCodec[java.math.BigDecimal]] = CTypes.CodecOf[java.math.BigDecimal]
    CodecVal.const(Type[A].as_??, Expr.quote(LeafCodecs.javaBigDecimal).asUntyped)
  }

  private def javaBigIntegerVal[A: Type]: CodecVal = {
    implicit val JBigIntegerT: Type[java.math.BigInteger] = CTypes.JBigIntegerT
    implicit val CodecBI: Type[JsonValueCodec[java.math.BigInteger]] = CTypes.CodecOf[java.math.BigInteger]
    CodecVal.const(Type[A].as_??, Expr.quote(LeafCodecs.javaBigInteger).asUntyped)
  }

  /** The codec of a sibling type: its val in the block when it has one, otherwise an inline `make` under the base config (primitives,
    * collections -- anything jsoniter derives on its own and we emit no val for).
    */
  private def refOrMake[S: Type](refs: CodecRefs, config: PicklerConfiguration): Expr[JsonValueCodec[S]] = {
    implicit val CodecS: Type[JsonValueCodec[S]] = CTypes.CodecOf[S]
    refs(Type[S].plainPrint).map(_.asTyped[JsonValueCodec[S]]).getOrElse(makeExpr[S](baseConfig(config)))
  }

  private def eitherUnit[A: Type](left: ??, right: ??, config: PicklerConfiguration): CodecVal = {
    import left.Underlying as L
    import right.Underlying as R
    implicit val CodecL: Type[JsonValueCodec[L]] = CTypes.CodecOf[L]
    implicit val CodecR: Type[JsonValueCodec[R]] = CTypes.CodecOf[R]
    // `A` *is* `Either[L, R]`; the cast only tells the quote so.
    implicit val EitherLR: Type[Either[L, R]] = Type[A].asInstanceOf[Type[Either[L, R]]]
    implicit val CodecEither: Type[JsonValueCodec[Either[L, R]]] = CTypes.CodecOf[Either[L, R]]
    CodecVal(
      Type[A].as_??,
      refs =>
        Expr
          .quote(CodecCombinators.either[L, R](Expr.splice(refOrMake[L](refs, config)), Expr.splice(refOrMake[R](refs, config))))
          .asUntyped
    )
  }

  /** `A = Option[Option[X]]` flattened: `Some(Some(x))` is `x`, `Some(None)` and `None` are `null` (or omitted as a field), and `null`
    * decodes as `None`. That is what the schema (`SOption(SOption(X))`, i.e. a nullable `X`) documents, and what the other tapir JSON
    * modules do.
    *
    * The val is for the *inner* `Option[X]`: jsoniter's field writer unwraps the outer `Option` itself (that is how `transientNone` works)
    * and only then looks for a codec, so a val for `A` would never be consulted from a field. At the root, `make[A]` is not an option
    * either (it would look the inner type up and find our val, but the outer `Some(None)` would then become `null` while `None` became...
    * also `null` -- fine -- yet the root type of the block must be `A`), so a second val wraps the inner one.
    */
  private def nestedOptionUnits[A: Type](inner: ??, element: ??, config: PicklerConfiguration, isRoot: Boolean): List[CodecVal] = {
    import inner.Underlying as I
    import element.Underlying as X
    implicit val CodecX: Type[JsonValueCodec[X]] = CTypes.CodecOf[X]
    implicit val OptionX: Type[Option[X]] = Type[I].asInstanceOf[Type[Option[X]]]
    implicit val CodecOptionX: Type[JsonValueCodec[Option[X]]] = CTypes.CodecOf[Option[X]]
    val innerVal =
      CodecVal(Type[I].as_??, refs => Expr.quote(CodecCombinators.option[X](Expr.splice(refOrMake[X](refs, config)))).asUntyped)
    if (!isRoot) List(innerVal)
    else {
      implicit val OptionI: Type[Option[I]] = Type[A].asInstanceOf[Type[Option[I]]]
      implicit val CodecI: Type[JsonValueCodec[I]] = CTypes.CodecOf[I]
      implicit val CodecOptionI: Type[JsonValueCodec[Option[I]]] = CTypes.CodecOf[Option[I]]
      val rootVal =
        CodecVal(Type[A].as_??, refs => Expr.quote(CodecCombinators.option[I](Expr.splice(refOrMake[I](refs, config)))).asUntyped)
      List(innerVal, rootVal)
    }
  }

  /** Visit `A` (nested somewhere under the root): recurse into its children, then emit its codec if it needs one.
    *
    * A user-supplied `Pickler[A]` short-circuits the visit: its `codec` becomes the implicit for `A`, and nothing beneath `A` is looked at
    * -- whatever that pickler does for its own fields is its business.
    */
  private def walk[A: Type](config: PicklerConfiguration, visited: Set[String], acc: Vector[CodecVal]): MIO[Walk] = {
    val key = Type[A].plainPrint
    if (visited.contains(key)) MIO.pure((visited, acc))
    else
      userPickler[A].flatMap {
        case Some(pickler) =>
          implicit val CodecA: Type[JsonValueCodec[A]] = CTypes.CodecOf[A]
          MIO.pure((visited + key, acc :+ CodecVal.const(Type[A].as_??, Expr.quote(Expr.splice(pickler).codec).asUntyped)))
        case None =>
          val shape = classify[A]
          rejectBareCodec[A](shape) >> walkChildren(childrenOf(shape), config, visited + key, acc).flatMap { case (v, a) =>
            codecValFor[A](shape, config, isRoot = false).map(units => (v, a ++ units))
          }
      }
  }

  /** A `given JsonValueCodec[A]` with no `given Pickler[A]`, for a structural `A`, is refused.
    *
    * It would not even be honoured: the `implicit lazy val codec$A` this derivation emits sits in a tighter scope than the user's given and
    * wins the implicit search inside jsoniter, so the user's codec would be silently ignored (measured). Had it won instead, the JSON would
    * follow the codec while the schema still documented the class. Either outcome is wrong; a `Pickler[A]` carries both halves and is
    * honoured by both chains. Leaf types are exempt (their schema comes from an implicit `Schema` anyway, which the user controls the same
    * way).
    */
  private def rejectBareCodec[A: Type](shape: Shape[A]): MIO[Unit] = shape match {
    case _: Shape.Product[?] | _: Shape.Enumeration[?] | _: Shape.Coproduct[?] =>
      implicit val CodecA: Type[JsonValueCodec[A]] = CTypes.CodecOf[A]
      Expr.summonImplicit[JsonValueCodec[A]].toOption match {
        case Some(codec) => fail(PicklerDerivationError.CodecWithoutPickler(Type[A].plainPrint, codec.plainPrint))
        case None        => MIO.pure(())
      }
    case _ => MIO.pure(())
  }

  private def walkChildren(children: List[??], config: PicklerConfiguration, visited: Set[String], acc: Vector[CodecVal]): MIO[Walk] =
    children.foldLeft(MIO.pure((visited, acc))) { (walkSoFar, child) =>
      walkSoFar.flatMap { case (v, a) =>
        import child.Underlying as Child
        walk[Child](config, v, a)
      }
    }

  // -----------------------------------------------------------------------------------------------------------------
  // Per-type configuration
  // -----------------------------------------------------------------------------------------------------------------

  /** The knobs every `make` gets. Non-structural roots (primitives, collections, `Option`, `Map`) need nothing more; jsoniter derives them
    * directly.
    */
  private def baseConfig(config: PicklerConfiguration): Expr[CodecMakerConfig] = {
    implicit val ConfigT: Type[CodecMakerConfig] = CTypes.MakerConfig
    implicit val BooleanT: Type[Boolean] = CTypes.BooleanT
    val transientNone = Expr(config.transientNone)
    Expr.quote {
      CodecMakerConfig
        .withTransientEmpty(false)
        .withTransientDefault(false)
        .withTransientNone(Expr.splice(transientNone))
        .withRequireDiscriminatorFirst(false)
        .withAllowRecursiveTypes(true)
    }
  }

  private def productConfig[A: Type](params: List[(String, Parameter)], config: PicklerConfiguration): MIO[Expr[CodecMakerConfig]] = {
    implicit val ConfigT: Type[CodecMakerConfig] = CTypes.MakerConfig
    implicit val StringT: Type[String] = CTypes.StringT
    implicit val OptionStringT: Type[Option[String]] = CTypes.OptionStringT
    implicit val StringPF: Type[PartialFunction[String, String]] = CTypes.StringPF
    implicit val StringFn: Type[String => String] = CTypes.StringFn

    val renames: Either[PicklerDerivationError, List[(String, String)]] =
      params.foldRight[Either[PicklerDerivationError, List[(String, String)]]](Right(Nil)) { case ((name, param), acc) =>
        for {
          tail <- acc
          encoded <- encodedFieldName[A](param, name, config)
        } yield if (encoded == name) tail else (name -> encoded) :: tail
      }

    (for { pairs <- renames; ownTag <- discriminatorValue[A](config) } yield (pairs, ownTag)) match {
      case Left(error)            => fail(error)
      case Right((pairs, ownTag)) =>
        // `alwaysEmitDiscriminator` needs a discriminator field name; jsoniter only acts on it when `A` has a sealed
        // parent, so setting both unconditionally is safe. The leaf mapper covers `A` itself, which is all a
        // stand-alone leaf codec can ever be asked about.
        val discriminator = Expr(Option(config.discriminator))
        val leafMapper = stringFunction(List(jsoniterLeafName[A] -> ownTag))
        val withoutFields = Expr.quote {
          Expr
            .splice(baseConfig(config))
            .withDiscriminatorFieldName(Expr.splice(discriminator))
            .withAdtLeafClassNameMapper(Expr.splice(leafMapper))
            .withAlwaysEmitDiscriminator(true)
        }
        val result =
          if (pairs.isEmpty) withoutFields
          else {
            val fieldMapper = stringPartialFunction(pairs)
            Expr.quote(Expr.splice(withoutFields).withFieldNameMapper(Expr.splice(fieldMapper)))
          }
        Log.info(s"${Type[A].prettyPrint}: field renames ${pairs.mkString("{", ", ", "}")}") >> MIO.pure(result)
    }
  }

  /** The configuration for a sealed hierarchy: bare strings for an enumeration (`Shape.Enumeration`), discriminated objects otherwise
    * (`Shape.Coproduct`).
    */
  private def hierarchyConfig[A: Type](
      leaves: List[(String, ??<:[A])],
      asEnumeration: Boolean,
      config: PicklerConfiguration
  ): MIO[Expr[CodecMakerConfig]] = {
    implicit val ConfigT: Type[CodecMakerConfig] = CTypes.MakerConfig
    implicit val StringT: Type[String] = CTypes.StringT // needed by `Expr(_: Option[String])`
    implicit val OptionStringT: Type[Option[String]] = CTypes.OptionStringT
    implicit val StringFn: Type[String => String] = CTypes.StringFn

    // Enumeration values are the cases' simple names, not discriminator values (`NameSupport.enumerationValue`);
    // `SchemaDerivation.deriveStringEnumSchema` documents the same literals.
    val values: Either[PicklerDerivationError, List[String]] =
      if (asEnumeration) Right(leaves.map { case (_, leaf) => import leaf.Underlying as Leaf; enumerationValue[Leaf] })
      else discriminatorValues(leaves, config)
    val jsoniterNames = leaves.map { case (_, leaf) => import leaf.Underlying as Leaf; jsoniterLeafName[Leaf] }

    if (leaves.isEmpty) fail(PicklerDerivationError.NoChildrenInSealedTrait(Type[A].plainPrint))
    else
      values match {
        case Left(error)   => fail(error)
        case Right(values) =>
          val mapping = jsoniterNames.zip(values)
          val discriminator = Expr(if (asEnumeration) Option.empty[String] else Some(config.discriminator))
          val leafMapper = stringFunction(mapping)
          val result = Expr.quote {
            Expr
              .splice(baseConfig(config))
              .withDiscriminatorFieldName(Expr.splice(discriminator))
              .withAdtLeafClassNameMapper(Expr.splice(leafMapper))
          }
          Log.info(
            s"${Type[A].prettyPrint}: ${if (asEnumeration) "string enum" else s"discriminated by '${config.discriminator}'"}, " +
              s"leaves ${mapping.mkString("{", ", ", "}")}"
          ) >> MIO.pure(result)
      }
  }
}
