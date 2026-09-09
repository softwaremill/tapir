package sttp.tapir.json.pickler.next.internal.compiletime

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.{CodecMakerConfig, JsonCodecMaker}
import hearth.MacroCommons
import hearth.fp.effect.*
import hearth.std.*
import sttp.tapir.Schema.SName
import sttp.tapir.json.pickler.next.PicklerConfiguration
import sttp.tapir.json.pickler.next.internal.runtime.LeafCodecs

/** Derivation of the `JsonValueCodec` half of a `Pickler`, by **configuring `JsonCodecMaker.make`** rather than by
  * generating reader/writer code ourselves.
  *
  * ==Shape of the generated code==
  * {{{
  * {
  *   implicit lazy val codec$Address: JsonValueCodec[Address] = JsonCodecMaker.make[Address](<config for Address>)
  *   implicit lazy val codec$Status:  JsonValueCodec[Status]  = JsonCodecMaker.make[Status](<config for Status>)
  *   JsonCodecMaker.make[Person](<config for Person>)
  * }
  * }}}
  * One `make` per case class and per sealed hierarchy in the type graph, each with its own configuration. jsoniter
  * finds the sibling codecs through implicit search when it meets a nested type, which is what allows every
  * configuration to be *local*: `fieldNameMapper` is keyed by bare field name, so a graph-wide mapper could not
  * express `@encodedName` on one class's `name` but not another's.
  *
  * ==Why the configuration is computed here==
  * jsoniter interprets its `CodecMakerConfig` argument at *its* expansion time, and only accepts trees made of
  * literals and stable references. tapir's `PicklerConfiguration` is a runtime value, so it is folded with `semiEval`
  * first, its `toEncodedName` / `toDiscriminatorValue` are *invoked* during our expansion, and the results are emitted
  * as literal `Match` cases (see [[PlatformSupport]]). The schema half computes the same names at runtime from the same
  * configuration, so the two agree by construction.
  *
  * ==Knobs, and why each is set==
  *   - `transientEmpty(false)`: empty collections are written as `[]` (the uPickle-based module did so).
  *   - `transientDefault(false)`: fields equal to their Scala default are still written.
  *   - `transientNone`: from `PicklerConfiguration.transientNone`.
  *   - `requireDiscriminatorFirst(false)`: the discriminator may appear anywhere in the object.
  *   - `allowRecursiveTypes(true)`: recursion is handled by jsoniter's own `def`s; the schema uses `SRef`.
  *   - `alwaysEmitDiscriminator(true)` on case classes: a leaf written on its own carries its discriminator, as the
  *     uPickle-based module's tagged writers did. It is also what makes per-leaf codecs composable into the hierarchy
  *     codec: jsoniter delegates to the leaf's implicit and the leaf writes the tag itself.
  *   - `discriminatorFieldName(None)` on a hierarchy whose leaves are all singletons: bare strings (plan §5.2).
  *
  * ==What is deliberately not supported==
  * tapir's `@default` annotation does not drive decoding: jsoniter fills a missing field only from a Scala default
  * parameter and has no hook for anything else (D5). Scala default parameters *are* honoured.
  */
trait CodecDerivation {
  this: MacroCommons & StdExtensions & AnnotationSupport & PlatformSupport & ImplicitPicklerSupport =>

  private[compiletime] object CTypes {
    def CodecOf[A: Type]: Type[JsonValueCodec[A]] = Type.of[JsonValueCodec[A]]
    lazy val MakerConfig: Type[CodecMakerConfig] = Type.of[CodecMakerConfig]
    lazy val StringT: Type[String] = Type.of[String]
    lazy val BooleanT: Type[Boolean] = Type.of[Boolean]
    lazy val OptionStringT: Type[Option[String]] = Type.of[Option[String]]
    lazy val StringPF: Type[PartialFunction[String, String]] = Type.of[PartialFunction[String, String]]
    lazy val StringFn: Type[String => String] = Type.of[String => String]
    lazy val AnyValT: Type[AnyVal] = Type.of[AnyVal]
    lazy val CharT: Type[Char] = Type.of[Char]
    lazy val JBigDecimalT: Type[java.math.BigDecimal] = Type.of[java.math.BigDecimal]
    lazy val JBigIntegerT: Type[java.math.BigInteger] = Type.of[java.math.BigInteger]
    lazy val EncodedName: Type[sttp.tapir.Schema.annotations.encodedName] = Type.of[sttp.tapir.Schema.annotations.encodedName]
  }

  /** One `implicit lazy val` of type `JsonValueCodec[tpe]` the generated code will contain, for a type nested somewhere
    * under the root: either a `JsonCodecMaker.make[tpe](config)` call or a hand-written leaf codec from [[LeafCodecs]].
    */
  private final case class CodecVal(tpe: ??, rhs: UntypedExpr)

  // -----------------------------------------------------------------------------------------------------------------
  // Entry point
  // -----------------------------------------------------------------------------------------------------------------

  /** Discriminator values that replace the configuration-derived ones for specific leaves, keyed by the leaf type's
    * `plainPrint`. Used by `oneOfUsingField`, which decides those values from a user function.
    */
  protected var leafNameOverrides: Map[String, String] = Map.empty

  def deriveCodec[A: Type](config: PicklerConfiguration): MIO[Expr[JsonValueCodec[A]]] =
    Log.namedScope(s"deriveCodec[${Type[A].prettyPrint}]") {
      implicit val CodecA: Type[JsonValueCodec[A]] = CTypes.CodecOf[A]
      for {
        units <- collectNested[A](config)
        rootConfig <- configFor[A](config)
        _ <- Log.info(s"Emitting ${units.size} nested codec(s): ${units.map(_.tpe.Underlying.plainPrint).mkString(", ")}")
      } yield {
        // The root gets an implicit too. `make[A]` itself never looks its own type up (jsoniter pre-seeds the root as
        // "no implicit"), but a *nested* `make[Leaf]` whose field refers back to `A` must find it: otherwise jsoniter
        // would inline `A` there, under the leaf's configuration -- whose leaf-name mapper knows only that one leaf.
        val root = leafCodecFor[A].getOrElse(CodecVal(Type[A].as_??, makeExpr[A](rootConfig).asUntyped))
        val vals = (units :+ root).zipWithIndex.map { case (unit, i) =>
          import unit.tpe.Underlying as U
          implicit val CodecU: Type[JsonValueCodec[U]] = CTypes.CodecOf[U]
          (s"codec$$${Type[U].shortName}$$$i", Type[JsonValueCodec[U]].asUntyped, unit.rhs)
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

  /** Every case class and sealed hierarchy reachable from `A` (excluding `A` itself), dependencies first.
    *
    * The classification mirrors `SchemaDerivation`'s rule order so that both halves take a type apart the same way:
    * `String` and `Array[Byte]` are scalars (not collections), a value class is its inner type, `Map` before
    * `Collection`, singletons before case classes, and hierarchies flattened to their leaves.
    */
  private def collectNested[A: Type](config: PicklerConfiguration): MIO[List[CodecVal]] =
    walkChildren(children(shapeOf[A]), config, Set(Type[A].plainPrint), Vector.empty).map(_._2.toList)

  private type Walk = (Set[String], Vector[CodecVal])

  /** How a type is taken apart. The order of the cases in `shapeOf` mirrors `SchemaDerivation`'s rule order. */
  private sealed trait Shape
  private object Shape {
    /** jsoniter knows it, or nothing can be done for it. */
    case object Leaf extends Shape
    final case class HandWritten(codec: CodecVal) extends Shape
    /** A wrapper jsoniter sees through: `AnyVal`, `Option`, collections, `Map` values. */
    final case class Transparent(inner: List[??]) extends Shape
    case object Singleton extends Shape
    final case class Product[A](cc: CaseClass[A], fields: List[??]) extends Shape
    final case class Coproduct[A](e: Enum[A], leaves: List[??]) extends Shape
  }

  private def shapeOf[A: Type]: Shape =
    if (isCodecLeaf[A]) Shape.Leaf
    else
      leafCodecFor[A] match {
        case Some(codec) => Shape.HandWritten(codec)
        case None        =>
          Type[A] match {
            case IsValueType(isValueType) if isAnyVal[A] =>
              import isValueType.Underlying as Inner
              Shape.Transparent(List(Type[Inner].as_??))
            case IsOption(isOption) =>
              import isOption.Underlying as Element
              Shape.Transparent(List(Type[Element].as_??))
            case IsMap(isMap) =>
              import isMap.Underlying as Pair
              Shape.Transparent(List(mapValueType[A, Pair](isMap.value)))
            case IsCollection(isCollection) =>
              import isCollection.Underlying as Element
              Shape.Transparent(List(Type[Element].as_??))
            case _ =>
              if (SingletonValue.parse[A].toEither.isRight) Shape.Singleton
              else
                CaseClass.parse[A].toEither match {
                  case Right(cc) =>
                    Shape.Product(cc, cc.primaryConstructor.totalParameters.flatten.toList.map { case (_, param) => param.tpe })
                  case Left(_) =>
                    Enum.parse[A].toEither match {
                      case Right(e) =>
                        val leaves = e.exhaustiveChildren.map(_.toList).getOrElse(e.directChildren.toList).map {
                          case (_, child) =>
                            import child.Underlying as Child
                            Type[Child].as_??
                        }
                        Shape.Coproduct(e, leaves)
                      // jsoniter either knows the type (java.time, UUID, ...) or the schema chain has already failed
                      // to find an implicit Schema for it.
                      case Left(_) => Shape.Leaf
                    }
                }
          }
      }

  private def mapValueType[A: Type, Pair: Type](isMap: IsMapOf[A, Pair]): ?? = {
    import isMap.Value
    Type[Value].as_??
  }

  private def children(shape: Shape): List[??] = shape match {
    case Shape.Transparent(inner) => inner
    case Shape.Product(_, fields) => fields
    case Shape.Coproduct(_, leaves) => leaves
    case _                        => Nil
  }

  private def makeUnit[A: Type](config: Expr[CodecMakerConfig]): CodecVal = CodecVal(Type[A].as_??, makeExpr[A](config).asUntyped)

  /** Visit `A` (nested somewhere under the root): recurse into its children, then emit its codec if it needs one.
    *
    * A user-supplied `Pickler[A]` short-circuits the visit: its `codec` becomes the implicit for `A`, and nothing
    * beneath `A` is looked at -- whatever that pickler does for its own fields is its business.
    */
  private def walk[A: Type](config: PicklerConfiguration, visited: Set[String], acc: Vector[CodecVal]): MIO[Walk] = {
    val key = Type[A].plainPrint
    if (visited.contains(key)) MIO.pure((visited, acc))
    else
      userPickler[A].flatMap {
        case Some(pickler) =>
          implicit val CodecA: Type[JsonValueCodec[A]] = CTypes.CodecOf[A]
          MIO.pure((visited + key, acc :+ CodecVal(Type[A].as_??, Expr.quote(Expr.splice(pickler).codec).asUntyped)))
        case None =>
          val shape = shapeOf[A]
          rejectBareCodec[A](shape) >> walkChildren(children(shape), config, visited + key, acc).flatMap { case (v, a) =>
            shape match {
              case Shape.HandWritten(codec) => MIO.pure((v, a :+ codec))
              case Shape.Product(cc, _)     =>
                productConfig[A](cc.asInstanceOf[CaseClass[A]], config).map(cfg => (v, a :+ makeUnit[A](cfg)))
              case Shape.Coproduct(e, _) =>
                coproductConfig[A](e.asInstanceOf[Enum[A]], config).map(cfg => (v, a :+ makeUnit[A](cfg)))
              case _ => MIO.pure((v, a))
            }
          }
      }
  }

  /** A `given JsonValueCodec[A]` with no `given Pickler[A]`, for a structural `A`, is refused.
    *
    * It would not even be honoured: the `implicit lazy val codec$A` this derivation emits sits in a tighter scope than
    * the user's given and wins the implicit search inside jsoniter, so the user's codec would be silently ignored
    * (measured). Had it won instead, the JSON would follow the codec while the schema still documented the class.
    * Either outcome is wrong; a `Pickler[A]` carries both halves and is honoured by both chains. Leaf types are exempt
    * (their schema comes from an implicit `Schema` anyway, which the user controls the same way).
    */
  private def rejectBareCodec[A: Type](shape: Shape): MIO[Unit] = shape match {
    case _: Shape.Product[?] | _: Shape.Coproduct[?] =>
      implicit val CodecA: Type[JsonValueCodec[A]] = CTypes.CodecOf[A]
      Expr.summonImplicit[JsonValueCodec[A]].toOption match {
        case Some(codec) =>
          val error = PicklerDerivationError.CodecWithoutPickler(Type[A].plainPrint, codec.plainPrint)
          Log.error(error.message) >> MIO.fail(error)
        case None => MIO.pure(())
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

  private def isAnyVal[A: Type]: Boolean = {
    implicit val AnyValT: Type[AnyVal] = CTypes.AnyValT
    Type[A] <:< Type[AnyVal]
  }

  /** Leaf types tapir has a `Schema` for but `JsonCodecMaker` cannot derive: supplied from [[LeafCodecs]]. */
  private def leafCodecFor[A: Type]: Option[CodecVal] = {
    implicit val JBigDecimalT: Type[java.math.BigDecimal] = CTypes.JBigDecimalT
    implicit val JBigIntegerT: Type[java.math.BigInteger] = CTypes.JBigIntegerT
    implicit val CodecBD: Type[JsonValueCodec[java.math.BigDecimal]] = CTypes.CodecOf[java.math.BigDecimal]
    implicit val CodecBI: Type[JsonValueCodec[java.math.BigInteger]] = CTypes.CodecOf[java.math.BigInteger]
    if (Type[A] =:= Type[java.math.BigDecimal])
      Some(CodecVal(Type[A].as_??, Expr.quote(LeafCodecs.javaBigDecimal).asUntyped))
    else if (Type[A] =:= Type[java.math.BigInteger])
      Some(CodecVal(Type[A].as_??, Expr.quote(LeafCodecs.javaBigInteger).asUntyped))
    else None
  }

  /** Scalar-like types the structural extractors would otherwise take apart; same set as `SchemaDerivation`. */
  private def isCodecLeaf[A: Type]: Boolean = {
    implicit val StringT: Type[String] = CTypes.StringT
    implicit val CharT: Type[Char] = CTypes.CharT
    Type[A] <:< Type[String] || Type[A] =:= Type[Char] || Type[A].plainPrint == "scala.Array[scala.Byte]"
  }

  // -----------------------------------------------------------------------------------------------------------------
  // Per-type configuration
  // -----------------------------------------------------------------------------------------------------------------

  /** Configuration for the root, whatever its shape. Non-structural roots (primitives, collections, `Option`, `Map`)
    * only need the base knobs; jsoniter derives them directly.
    */
  private def configFor[A: Type](config: PicklerConfiguration): MIO[Expr[CodecMakerConfig]] =
    shapeOf[A] match {
      case Shape.Product(cc, _)  => productConfig[A](cc.asInstanceOf[CaseClass[A]], config)
      case Shape.Coproduct(e, _) => coproductConfig[A](e.asInstanceOf[Enum[A]], config)
      case _                     => MIO.pure(baseConfig(config))
    }

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

  private def productConfig[A: Type](cc: CaseClass[A], config: PicklerConfiguration): MIO[Expr[CodecMakerConfig]] = {
    implicit val ConfigT: Type[CodecMakerConfig] = CTypes.MakerConfig
    implicit val StringT: Type[String] = CTypes.StringT
    implicit val OptionStringT: Type[Option[String]] = CTypes.OptionStringT
    implicit val StringPF: Type[PartialFunction[String, String]] = CTypes.StringPF
    implicit val StringFn: Type[String => String] = CTypes.StringFn

    val params = cc.primaryConstructor.totalParameters.flatten.toList
    val renames: Either[PicklerDerivationError, List[(String, String)]] =
      params.foldRight[Either[PicklerDerivationError, List[(String, String)]]](Right(Nil)) { case ((name, param), acc) =>
        for {
          tail <- acc
          encoded <- encodedFieldName[A](param, name, config)
        } yield if (encoded == name) tail else (name -> encoded) :: tail
      }

    renames match {
      case Left(error) => Log.error(error.message) >> MIO.fail(error)
      case Right(pairs) =>
        // `alwaysEmitDiscriminator` needs a discriminator field name; jsoniter only acts on it when `A` has a sealed
        // parent, so setting both unconditionally is safe. The leaf mapper covers `A` itself, which is all a
        // stand-alone leaf codec can ever be asked about.
        val discriminator = Expr(Option(config.discriminator))
        val leafMapper = stringFunction(List(jsoniterLeafName[A] -> discriminatorValue[A](config)))
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

  private def coproductConfig[A: Type](e: Enum[A], config: PicklerConfiguration): MIO[Expr[CodecMakerConfig]] = {
    implicit val ConfigT: Type[CodecMakerConfig] = CTypes.MakerConfig
    implicit val StringT: Type[String] = CTypes.StringT
    implicit val OptionStringT: Type[Option[String]] = CTypes.OptionStringT
    implicit val StringFn: Type[String => String] = CTypes.StringFn

    val leaves = e.exhaustiveChildren.map(_.toList).getOrElse(e.directChildren.toList)
    if (leaves.isEmpty) {
      val error = PicklerDerivationError.NoChildrenInSealedTrait(Type[A].plainPrint)
      Log.error(error.message) >> MIO.fail(error)
    } else {
      // Plan §5.2: all leaves singletons => bare strings; otherwise => discriminated objects. `SchemaDerivation`
      // makes the same test (`SString` + enumeration validator vs `SCoproduct`).
      val allSingletons = leaves.forall { case (_, leaf) =>
        import leaf.Underlying as Leaf
        SingletonValue.parse[Leaf].toEither.isRight
      }
      val mapping = leaves.map { case (_, leaf) =>
        import leaf.Underlying as Leaf
        jsoniterLeafName[Leaf] -> discriminatorValue[Leaf](config)
      }
      val discriminator = Expr(if (allSingletons) Option.empty[String] else Some(config.discriminator))
      val leafMapper = stringFunction(mapping)
      val result = Expr.quote {
        Expr
          .splice(baseConfig(config))
          .withDiscriminatorFieldName(Expr.splice(discriminator))
          .withAdtLeafClassNameMapper(Expr.splice(leafMapper))
      }
      Log.info(
        s"${Type[A].prettyPrint}: ${if (allSingletons) "string enum" else s"discriminated by '${config.discriminator}'"}, " +
          s"leaves ${mapping.mkString("{", ", ", "}")}"
      ) >> MIO.pure(result)
    }
  }

  // -----------------------------------------------------------------------------------------------------------------
  // Names — computed once, exactly as the schema half computes them at runtime
  // -----------------------------------------------------------------------------------------------------------------

  /** `@encodedName` if present, otherwise `config.toEncodedName(name)` — what `SchemaUtils.productField` does. */
  private def encodedFieldName[A: Type](
      param: Parameter,
      name: String,
      config: PicklerConfiguration
  ): Either[PicklerDerivationError, String] =
    literalEncodedFieldName[A](param, name) match {
      case Right(Some(explicit)) => Right(explicit)
      case Right(None)           => Right(config.toEncodedName(name))
      case Left(detail)          => Left(PicklerDerivationError.InvalidAnnotation(detail))
    }

  /** `config.toDiscriminatorValue(<SName of A>)`, with the `SName` built as `SchemaDerivation.sNameExpr` builds it: a
    * type-level `@encodedName` replaces the whole name, otherwise it is core's `typeFullName`. Only `fullName` matters
    * to `toDiscriminatorValue`, so type arguments are not reproduced here.
    */
  private def discriminatorValue[A: Type](config: PicklerConfiguration): String =
    leafNameOverrides.getOrElse(Type[A].plainPrint, configDiscriminatorValue[A](config))

  private def configDiscriminatorValue[A: Type](config: PicklerConfiguration): String = {
    implicit val EncodedNameT: Type[sttp.tapir.Schema.annotations.encodedName] = CTypes.EncodedName
    val fullName = Type[A]
      .annotationsOfType[sttp.tapir.Schema.annotations.encodedName]
      .headOption
      .flatMap(literalStringArg(_))
      .getOrElse(tapirFullName[A])
    config.toDiscriminatorValue(SName(fullName))
  }
}
