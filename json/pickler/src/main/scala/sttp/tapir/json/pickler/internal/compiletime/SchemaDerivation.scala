package sttp.tapir.json.pickler.internal.compiletime

import hearth.MacroCommons
import hearth.fp.effect.*
import hearth.std.*
import sttp.tapir.Schema
import sttp.tapir.Schema.SName
import sttp.tapir.SchemaType.SProductField
import sttp.tapir.internal.SNameMacros
import sttp.tapir.json.pickler.PicklerConfiguration
import sttp.tapir.json.pickler.internal.runtime.SchemaUtils

/** Derivation of the tapir [[Schema]] half of a `Pickler`.
  *
  * The shapes produced here are pinned by the incumbent module's `SchemaDerivationTest`; see `doc/dev/schema-derivation-test-spec.md`.
  *
  * ==Design==
  * Name transformations (`toEncodedName`, `toDiscriminatorValue`) are applied at **runtime**, by passing the `PicklerConfiguration`
  * expression through to [[SchemaUtils]], rather than being folded at compile time. That is what makes all eight `with*DiscriminatorValues`
  * variants and all three member-name variants work without a single compile-time branch — and it matches the incumbent, which emits
  * `config.toEncodedName(name)` into the tree too.
  */
trait SchemaDerivation { this: MacroCommons & StdExtensions & AnnotationSupport & ImplicitPicklerSupport & PlatformSupport =>

  /** Centralised `Type.of` instances — see the note on `PicklerMacrosImpl.PTypes` for why these are not `implicit val`s at their use sites.
    */
  private[compiletime] object STypes {
    def SchemaOf[A: Type]: Type[Schema[A]] = Type.of[Schema[A]]
    def SchemaAny: Type[Schema[Any]] = Type.of[Schema[Any]]
    def ProductFieldOf[A: Type]: Type[SProductField[A]] = Type.of[SProductField[A]]
    lazy val SNameT: Type[SName] = Type.of[SName]
    lazy val StringT: Type[String] = Type.of[String]
    lazy val CharT: Type[Char] = Type.of[Char]
    lazy val AnyT: Type[Any] = Type.of[Any]
    lazy val AnyValT: Type[AnyVal] = Type.of[AnyVal]
    lazy val ListAnyT: Type[List[Any]] = Type.of[List[Any]]
    lazy val ListStringT: Type[List[String]] = Type.of[List[String]]
    def ListOf[A: Type]: Type[List[A]] = Type.of[List[A]]
    lazy val EncodedName: Type[Schema.annotations.encodedName] = Type.of[Schema.annotations.encodedName]
    lazy val EitherCtor: Type.Ctor2[Either] = Type.Ctor2.of[Either]
  }

  // -----------------------------------------------------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------------------------------------------------

  /** Everything a schema rule needs, threaded through the recursion.
    *
    * `inProgress` is the recursion guard: a type is added before its children are derived and restored (not merely removed) afterwards, so
    * that sibling branches do not see each other's entries.
    */
  final case class SchemaCtx[A](
      tpe: Type[A],
      config: Expr[PicklerConfiguration],
      cache: MLocal[ValDefsCache],
      inProgress: MLocal[Set[String]],
      derivedType: Option[??]
  ) {
    def cacheKey: String = SchemaDerivation.this.cacheKey(tpe)

    def nest[B: Type]: SchemaCtx[B] = SchemaCtx(Type[B], config, cache, inProgress, derivedType)
  }

  def sctx[A](implicit A: SchemaCtx[A]): SchemaCtx[A] = A

  implicit def currentSchemaType[A: SchemaCtx]: Type[A] = sctx.tpe

  private def cacheKey[A](tpe: Type[A]): String = s"pickler-schema-for-${tpe.plainPrint}"

  private def cacheName[A: Type]: String = s"schema_${Type[A].shortName}"

  /** Hoist a schema into a `lazy val` and return a reference to it.
    *
    * `lazy` (rather than `val`) is what lets mutually recursive schemas reference each other; hoisting at all is what stops a deep ADT from
    * inlining the same sub-schema at every occurrence.
    */
  private def setCachedAndGet[A: Type](
      cache: MLocal[ValDefsCache],
      instance: Expr[Schema[A]]
  ): MIO[Expr[Schema[A]]] = {
    val key = cacheKey(Type[A])
    implicit val SchemaA: Type[Schema[A]] = STypes.SchemaOf[A]
    cache.get0Ary[Schema[A]](key).flatMap {
      case Some(ref) => MIO.pure(ref)
      case None      =>
        cache.buildCachedWith(key, ValDefBuilder.ofLazy[Schema[A]](cacheName[A]))(_ => instance) >>
          cache.get0Ary[Schema[A]](key).map(_.getOrElse(instance))
    }
  }

  /** Run `body` with `A` marked as in-progress, restoring the previous set afterwards. */
  private def guardingRecursion[A: SchemaCtx, Out](body: => MIO[Out]): MIO[Out] =
    sctx.inProgress.get.flatMap { previous =>
      for {
        _ <- sctx.inProgress.set(previous + sctx.cacheKey)
        result <- body
        _ <- sctx.inProgress.set(previous)
      } yield result
    }

  // -----------------------------------------------------------------------------------------------------------------
  // Rule pipeline
  // -----------------------------------------------------------------------------------------------------------------

  abstract class SchemaRule(val name: String) extends Rule {
    def apply[A: SchemaCtx]: MIO[Rule.Applicability[Expr[Schema[A]]]]
  }

  def deriveSchemaFor[A: SchemaCtx]: MIO[Expr[Schema[A]]] =
    Log.namedScope(s"deriveSchema[${Type[A].prettyPrint}]") {
      Rules(
        UseCachedRule,
        UseSelfRefWhenRecursiveRule,
        // A user-supplied `Pickler[A]` beats everything structural: it is the incumbent's override mechanism, and the
        // codec chain honours the same instance, so schema and JSON stay in step. Safe to summon early only because
        // of the re-entrancy guard described in `ImplicitPicklerSupport`.
        UseUserPicklerRule,
        // Every structural rule precedes the implicit search. Two reasons, and the ordering is load-bearing:
        //
        //  1. Summoning tapir's own `Schema[Option[A]]` would make the *compiler* search for `Schema[A]`, and that
        //     inner search is not under our control — with `sttp.tapir.generic.auto.*` in scope it resolves to
        //     tapir's Mirror/magnolia derivation, silently producing a schema built by different rules.
        //  2. Hearth's `summonExprIgnoring` (which is how one would otherwise exclude `Schema.derivedSchema`) is
        //     only available on Scala 3.7+, and tapir builds Scala 3 at 3.3.8. Ordering gives us the same guarantee
        //     without the API. See the note on `UseImplicitRule`.
        UseBuiltInLeafRule,
        HandleAsValueClassRule,
        HandleAsOptionRule,
        HandleAsEitherRule,
        HandleAsMapRule,
        HandleAsCollectionRule,
        HandleAsSingletonRule,
        HandleAsCaseClassRule,
        HandleAsEnumRule,
        UseImplicitRule
      )(_[A]).flatMap {
        case Right(result) => MIO.pure(result)
        case Left(reasons) =>
          val explanation = reasons.toListMap.view
            .map { case (rule, why) =>
              if (why.isEmpty) s"  - ${rule.name}: not applicable"
              else s"  - ${rule.name}: ${why.mkString("; ")}"
            }
            .mkString("\n")
          MIO.fail(
            new Exception(
              s"Cannot derive a tapir Schema for ${Type[A].plainPrint}: no implicit Schema was found and the type is " +
                s"not an Option, collection, Map, singleton, case class or sealed hierarchy.\n" + explanation
            )
          )
      }
    }

  private object UseCachedRule extends SchemaRule("use cached schema") {
    def apply[A: SchemaCtx]: MIO[Rule.Applicability[Expr[Schema[A]]]] = {
      implicit val SchemaA: Type[Schema[A]] = STypes.SchemaOf[A]
      sctx.cache.get0Ary[Schema[A]](sctx.cacheKey).map {
        case Some(cached) => Rule.matched(cached)
        case None         => Rule.yielded(s"${Type[A].plainPrint} is not cached")
      }
    }
  }

  private object UseSelfRefWhenRecursiveRule extends SchemaRule("emit SRef for a recursive type") {
    def apply[A: SchemaCtx]: MIO[Rule.Applicability[Expr[Schema[A]]]] =
      sctx.inProgress.get.map { inProgress =>
        if (inProgress.contains(sctx.cacheKey)) {
          implicit val SNameT: Type[SName] = STypes.SNameT
          val sName = sNameExpr[A]
          Rule.matched(Expr.quote(SchemaUtils.refSchema[A](Expr.splice(sName))))
        } else Rule.yielded(s"${Type[A].plainPrint} is not currently being derived")
      }
  }

  private object UseUserPicklerRule extends SchemaRule("use the schema of a user-supplied Pickler") {
    def apply[A: SchemaCtx]: MIO[Rule.Applicability[Expr[Schema[A]]]] =
      userPickler[A].flatMap {
        case Some(pickler) =>
          implicit val SchemaA: Type[Schema[A]] = STypes.SchemaOf[A]
          setCachedAndGet[A](sctx.cache, Expr.quote(Expr.splice(pickler).schema)).map(Rule.matched)
        case None => MIO.pure(Rule.yielded(s"no user-supplied Pickler[${Type[A].plainPrint}] in scope"))
      }
  }

  /** Terminal rule: pick up whatever `Schema[A]` is in implicit scope.
    *
    * This is where primitives, `String`, `java.time` types and any user-provided schema for a non-structural type are resolved —
    * `Schema.schemaForInt` and friends live on the `Schema` companion and need no import.
    *
    * Running *last* is deliberate. `Schema.derivedSchema` (in `LowPrioritySchema`) unwraps a `Derived[Schema[T]]` produced by
    * `sttp.tapir.generic.auto.schemaForCaseClass`; if a user has that import in scope, an early implicit search would resolve every case
    * class through magnolia instead of through our rules, which name types and fold annotations differently. Because structural rules are
    * tried first, a case class, sealed hierarchy, `Option`, collection or `Map` never reaches this rule.
    *
    * The cost is that a user-supplied `given Schema[MyCaseClass]` does not override structural derivation. That matches the incumbent,
    * where overriding is done by supplying a `Pickler`, not a `Schema`.
    */
  private object UseImplicitRule extends SchemaRule("use an implicit Schema") {
    def apply[A: SchemaCtx]: MIO[Rule.Applicability[Expr[Schema[A]]]] = {
      implicit val SchemaA: Type[Schema[A]] = STypes.SchemaOf[A]
      MIO.pure(Expr.summonImplicit[Schema[A]].toOption match {
        case Some(expr) => Rule.matched(expr)
        case None       => Rule.yielded(s"no implicit Schema[${Type[A].plainPrint}] in scope")
      })
    }
  }

  /** Types that tapir models as scalars but that the structural rules would happily take apart.
    *
    * `String` is the motivating case: it is an `Iterable[Char]`, so `IsCollection` matches it and the collection rule would emit
    * `SArray[String, Char]` instead of `SString`. `Array[Byte]` is the same story with `SBinary`.
    *
    * Summoning early is safe for exactly these types because they are not case classes or sealed hierarchies, so `sttp.tapir.generic.auto`
    * cannot produce a competing instance for them — the concern that keeps the general implicit search at the end of the chain does not
    * apply.
    */
  private def isBuiltInLeaf[A: Type]: Boolean = {
    implicit val StringT: Type[String] = STypes.StringT
    Type[A] <:< Type[String] || Type[A].plainPrint == "scala.Array[scala.Byte]"
  }

  private object UseBuiltInLeafRule extends SchemaRule("use tapir's built-in schema for a scalar-like type") {
    def apply[A: SchemaCtx]: MIO[Rule.Applicability[Expr[Schema[A]]]] = {
      implicit val CharT: Type[Char] = STypes.CharT
      if (Type[A] =:= Type[Char])
        // tapir core declares no `Schema[Char]`, but jsoniter writes a `Char` as a one-character string. Supplying the
        // matching `SString` here is what keeps the schema and the codec in agreement.
        MIO.pure(Rule.matched(Expr.quote(SchemaUtils.stringLikeSchema[A])))
      else if (!isBuiltInLeaf[A]) MIO.pure(Rule.yielded(s"${Type[A].plainPrint} is not a scalar-like built-in"))
      else {
        implicit val SchemaA: Type[Schema[A]] = STypes.SchemaOf[A]
        MIO.pure(Expr.summonImplicit[Schema[A]].toOption match {
          case Some(expr) => Rule.matched(expr)
          case None       => Rule.yielded(s"no built-in Schema[${Type[A].plainPrint}] in scope")
        })
      }
    }
  }

  /** An `AnyVal` wrapper is documented as its inner type, because that is how jsoniter writes it (unwrapped). Matches tapir core's own
    * `Schema.derived` for value classes. Restricted to `AnyVal`: Hearth's `IsValueType` also matches opaque types and Java boxes, which
    * jsoniter does not unwrap.
    */
  private object HandleAsValueClassRule extends SchemaRule("handle as AnyVal value class") {
    def apply[A: SchemaCtx]: MIO[Rule.Applicability[Expr[Schema[A]]]] = {
      implicit val AnyValT: Type[AnyVal] = STypes.AnyValT
      Type[A] match {
        case IsValueType(isValueType) if Type[A] <:< Type[AnyVal] =>
          import isValueType.Underlying as Inner
          deriveSchemaFor[Inner](using sctx.nest[Inner]).map { inner =>
            Rule.matched(Expr.quote(Expr.splice(inner).asInstanceOf[Schema[A]]))
          }
        case _ => MIO.pure(Rule.yielded(s"${Type[A].plainPrint} is not an AnyVal value class"))
      }
    }
  }

  private object HandleAsOptionRule extends SchemaRule("handle as Option") {
    def apply[A: SchemaCtx]: MIO[Rule.Applicability[Expr[Schema[A]]]] = Type[A] match {
      case IsOption(isOption) =>
        import isOption.Underlying as Element
        deriveSchemaFor[Element](using sctx.nest[Element]).map { element =>
          Rule.matched(Expr.quote {
            SchemaUtils.optionSchema[Element](Expr.splice(element)).asInstanceOf[Schema[A]]
          })
        }
      case _ => MIO.pure(Rule.yielded(s"${Type[A].plainPrint} is not an Option"))
    }
  }

  /** `Either` is a sealed hierarchy, so without this rule it would fall through to `HandleAsEnumRule` and be documented as a discriminated
    * coproduct of `Left`/`Right`. tapir core documents it as an *untagged* coproduct of the two sides (`Schema.schemaForEither`), and the
    * codec writes the bare side value to match (plan §5.3).
    */
  private object HandleAsEitherRule extends SchemaRule("handle as Either") {
    def apply[A: SchemaCtx]: MIO[Rule.Applicability[Expr[Schema[A]]]] = {
      val EitherCtor = STypes.EitherCtor
      Type[A] match {
        case EitherCtor(left, right) =>
          import left.Underlying as L
          import right.Underlying as R
          for {
            l <- deriveSchemaFor[L](using sctx.nest[L])
            r <- deriveSchemaFor[R](using sctx.nest[R])
          } yield Rule.matched(Expr.quote(SchemaUtils.eitherSchema[L, R](Expr.splice(l), Expr.splice(r)).asInstanceOf[Schema[A]]))
        case _ => MIO.pure(Rule.yielded(s"${Type[A].plainPrint} is not an Either"))
      }
    }
  }

  private object HandleAsCollectionRule extends SchemaRule("handle as collection") {
    def apply[A: SchemaCtx]: MIO[Rule.Applicability[Expr[Schema[A]]]] = Type[A] match {
      case IsCollection(isCollection) =>
        import isCollection.Underlying as Element
        deriveSchemaFor[Element](using sctx.nest[Element]).map { element =>
          Rule.matched(Expr.quote {
            SchemaUtils.collectionSchema[Element](Expr.splice(element)).asInstanceOf[Schema[A]]
          })
        }
      case _ => MIO.pure(Rule.yielded(s"${Type[A].plainPrint} is not a collection"))
    }
  }

  private object HandleAsMapRule extends SchemaRule("handle as Map") {
    def apply[A: SchemaCtx]: MIO[Rule.Applicability[Expr[Schema[A]]]] = Type[A] match {
      case IsMap(isMap) =>
        import isMap.Underlying as Pair
        deriveMapSchema[A, Pair](isMap.value)
      case _ => MIO.pure(Rule.yielded(s"${Type[A].plainPrint} is not a Map"))
    }

    private def deriveMapSchema[A: SchemaCtx, Pair: Type](
        isMap: IsMapOf[A, Pair]
    ): MIO[Rule.Applicability[Expr[Schema[A]]]] = {
      import isMap.{Key, Value}
      implicit val StringT: Type[String] = STypes.StringT
      if (!(Key <:< Type[String]))
        MIO.fail(
          new Exception(
            s"Cannot derive a tapir Schema for a Map with non-String keys (${Key.plainPrint}); " +
              "use Pickler.picklerForMap with an explicit key encoder."
          )
        )
      else {
        // tapir's own convention (`SchemaMacros.generateSchemaForMap`): a `String` key contributes nothing to the
        // name, and the value's type arguments are flattened in after it.
        val typeParams = Expr(SchemaUtils.flattenTypeName(Value.plainPrint))
        deriveSchemaFor[Value](using sctx.nest[Value]).map { value =>
          Rule.matched(Expr.quote {
            SchemaUtils.mapSchema[Value](Expr.splice(value), Expr.splice(typeParams)).asInstanceOf[Schema[A]]
          })
        }
      }
    }
  }

  private object HandleAsSingletonRule extends SchemaRule("handle as singleton") {
    def apply[A: SchemaCtx]: MIO[Rule.Applicability[Expr[Schema[A]]]] =
      SingletonValue.parse[A].toEither match {
        case Right(_)     => guardingRecursion[A, Expr[Schema[A]]](deriveSingletonSchema[A]).map(Rule.matched)
        case Left(reason) => MIO.pure(Rule.yielded(reason))
      }
  }

  private object HandleAsCaseClassRule extends SchemaRule("handle as case class") {
    def apply[A: SchemaCtx]: MIO[Rule.Applicability[Expr[Schema[A]]]] =
      // A tuple parses as a case class, but jsoniter writes it as an array: no schema would agree with the codec.
      if (Type[A].isTuple) MIO.fail(PicklerDerivationError.TupleNotSupported(Type[A].plainPrint))
      else
        CaseClass.parse[A].toEither match {
          case Right(cc)    => guardingRecursion[A, Expr[Schema[A]]](deriveCaseClassSchema[A](cc)).map(Rule.matched)
          case Left(reason) => MIO.pure(Rule.yielded(reason))
        }
  }

  private object HandleAsEnumRule extends SchemaRule("handle as sealed hierarchy / enum") {
    def apply[A: SchemaCtx]: MIO[Rule.Applicability[Expr[Schema[A]]]] =
      Enum.parse[A].toEither match {
        case Right(e)     => guardingRecursion[A, Expr[Schema[A]]](deriveEnumSchema[A](e)).map(Rule.matched)
        case Left(reason) => MIO.pure(Rule.yielded(reason.toString))
      }
  }

  // -----------------------------------------------------------------------------------------------------------------
  // Shapes
  // -----------------------------------------------------------------------------------------------------------------

  /** A `case object` becomes a product with no fields — the discriminator field, if any, is added by the parent. */
  private def deriveSingletonSchema[A: SchemaCtx]: MIO[Expr[Schema[A]]] = {
    implicit val SchemaA: Type[Schema[A]] = STypes.SchemaOf[A]
    implicit val SNameT: Type[SName] = STypes.SNameT
    val name = sNameExpr[A]
    val annotations = typeAnnotationsExpr[A]
    setCachedAndGet[A](
      sctx.cache,
      Expr.quote {
        SchemaUtils.enrichSchema[A](
          SchemaUtils.productSchema[A](Expr.splice(name), SchemaUtils.emptyFieldList[A]),
          Expr.splice(annotations)
        )
      }
    )
  }

  private def deriveCaseClassSchema[A: SchemaCtx](cc: CaseClass[A]): MIO[Expr[Schema[A]]] = {
    implicit val SchemaA: Type[Schema[A]] = STypes.SchemaOf[A]
    implicit val SNameT: Type[SName] = STypes.SNameT
    implicit val FieldT: Type[SProductField[A]] = STypes.ProductFieldOf[A]

    val name = sNameExpr[A]
    val annotations = typeAnnotationsExpr[A]
    val params = cc.primaryConstructor.totalParameters.flatten.toList

    params
      .foldLeft(MIO.pure(List.empty[Expr[SProductField[A]]])) { case (acc, (fieldName, param)) =>
        acc.flatMap(fields => deriveFieldExpr[A](fieldName, param).map(fields :+ _))
      }
      .flatMap { fields =>
        val fieldsList = fields.foldRight(Expr.quote(SchemaUtils.emptyFieldList[A])) { (field, tail) =>
          Expr.quote(Expr.splice(field) :: Expr.splice(tail))
        }
        setCachedAndGet[A](
          sctx.cache,
          Expr.quote {
            SchemaUtils.enrichSchema[A](
              SchemaUtils.productSchema[A](Expr.splice(name), Expr.splice(fieldsList)),
              Expr.splice(annotations)
            )
          }
        )
      }
  }

  private def deriveFieldExpr[A: SchemaCtx](fieldName: String, param: Parameter): MIO[Expr[SProductField[A]]] = {
    import param.tpe.Underlying as Field
    implicit val AnyT: Type[Any] = STypes.AnyT
    implicit val FieldT: Type[SProductField[A]] = STypes.ProductFieldOf[A]

    val scalaName = Expr(fieldName)
    val index = Expr(param.index)
    val annotations = collectAnnotationsExpr(allParamAnnotations[A](param, fieldName))
    val config = sctx.config

    deriveSchemaFor[Field](using sctx.nest[Field]).map { fieldSchema =>
      Expr.quote {
        SchemaUtils.productField[A](
          Expr.splice(scalaName),
          Expr.splice(config).toEncodedName(Expr.splice(scalaName)),
          Expr.splice(fieldSchema).asInstanceOf[Schema[Any]],
          Expr.splice(index),
          Expr.splice(annotations)
        )
      }
    }
  }

  /** Sealed hierarchies are flattened to their **leaves**: an intermediate sealed trait contributes its own children, not itself.
    * `Pet -> Rodent -> Hamster` must yield `Hamster` as a direct subtype.
    */
  private def deriveEnumSchema[A: SchemaCtx](e: Enum[A]): MIO[Expr[Schema[A]]] = {
    implicit val SchemaA: Type[Schema[A]] = STypes.SchemaOf[A]
    implicit val SchemaAnyT: Type[Schema[Any]] = STypes.SchemaAny
    implicit val SNameT: Type[SName] = STypes.SNameT

    val name = sNameExpr[A]
    val annotations = typeAnnotationsExpr[A]
    val config = sctx.config
    val children = e.exhaustiveChildren.map(_.toList).getOrElse(e.directChildren.toList)

    // Plan §5.2: a hierarchy whose leaves are *all* singletons is documented as a string with an enumeration
    // validator, not as a coproduct -- because that is how it is encoded. `CodecDerivation.coproductConfig` makes the
    // same test; if the two ever disagree, the schema will document an object while the codec writes a string.
    val allSingletons = children.nonEmpty && children.forall { case (_, child) =>
      import child.Underlying as Child
      SingletonValue.parse[Child].toEither.isRight
    }

    if (allSingletons) deriveStringEnumSchema[A](children)
    else
      children
        .foldLeft(MIO.pure(List.empty[Expr[Schema[Any]]])) { case (acc, (_, child)) =>
          acc.flatMap { schemas =>
            import child.Underlying as Child
            deriveSchemaFor[Child](using sctx.nest[Child]).map { childSchema =>
              schemas :+ Expr.quote(Expr.splice(childSchema).asInstanceOf[Schema[Any]])
            }
          }
        }
        .flatMap { childSchemas =>
          val subtypes = childSchemas.foldRight(Expr.quote(Nil: List[Schema[Any]])) { (childSchema, tail) =>
            Expr.quote(Expr.splice(childSchema) :: Expr.splice(tail))
          }
          setCachedAndGet[A](
            sctx.cache,
            Expr.quote {
              SchemaUtils.enrichSchema[A](
                SchemaUtils.coproductSchema[A](Expr.splice(name), Expr.splice(subtypes), Expr.splice(config)),
                Expr.splice(annotations)
              )
            }
          )
        }
  }

  /** `SString` plus a `Validator.enumeration` of the singleton values — the schema counterpart of encoding an all-singleton hierarchy as a
    * bare string.
    */
  private def deriveStringEnumSchema[A: SchemaCtx](children: List[(String, ??<:[A])]): MIO[Expr[Schema[A]]] = {
    implicit val SchemaA: Type[Schema[A]] = STypes.SchemaOf[A]
    implicit val SNameT: Type[SName] = STypes.SNameT
    implicit val ListA: Type[List[A]] = STypes.ListOf[A]
    implicit val ListStringT: Type[List[String]] = STypes.ListStringT
    implicit val StringT: Type[String] = STypes.StringT

    val name = sNameExpr[A]
    val annotations = typeAnnotationsExpr[A]

    val values = singletonValuesExpr[A](children)
    // Each case is written as its simple name (`PlatformSupport.enumCaseName`), the same literal the codec's leaf-name
    // mapper produces -- computed once here, independent of the configuration.
    val encodedNames = children.foldRight(Expr.quote(Nil: List[String])) { case ((_, child), tail) =>
      import child.Underlying as Child
      val childName = Expr(enumCaseName[Child](typeEncodedName[Child]))
      Expr.quote(Expr.splice(childName) :: Expr.splice(tail))
    }

    setCachedAndGet[A](
      sctx.cache,
      Expr.quote {
        SchemaUtils.enrichSchema[A](
          SchemaUtils.stringEnumSchema[A](Expr.splice(name), Expr.splice(values), Expr.splice(encodedNames)),
          Expr.splice(annotations)
        )
      }
    )
  }

  /** `List(Case1, Case2, ...)` for the singleton children of an enumeration; the caller guarantees they are singletons. */
  protected def singletonValuesExpr[A: Type](children: List[(String, ??<:[A])]): Expr[List[A]] = {
    implicit val ListA: Type[List[A]] = STypes.ListOf[A]
    children.foldRight(Expr.quote(Nil: List[A])) { case ((_, child), tail) =>
      import child.Underlying as Child
      val singleton = SingletonValue.parse[Child].toEither.toOption.get
      Expr.quote(Expr.splice(singleton.singletonExpr).asInstanceOf[A] :: Expr.splice(tail))
    }
  }

  // -----------------------------------------------------------------------------------------------------------------
  // Names and annotations
  // -----------------------------------------------------------------------------------------------------------------

  /** The `SName` for `A`: either its `@encodedName`, which replaces the name wholesale, or its fully-qualified type name parsed into base
    * name + flattened, fully-qualified type arguments.
    */
  protected def sNameExpr[A: Type]: Expr[SName] = {
    implicit val SNameT: Type[SName] = STypes.SNameT
    implicit val EncodedNameT: Type[Schema.annotations.encodedName] = STypes.EncodedName
    // `plainPrint`, not `prettyPrint`: the latter embeds ANSI escapes, which must never reach a string literal in
    // generated code.
    // Only the type's *own* `@encodedName` is consulted: the incumbent deliberately does not propagate a parent's
    // renaming to its subtypes (`SchemaDerivationTest`: "Not propagate type encodedName to subtypes of a sealed
    // trait, but keep inheritance for fields").
    typeEncodedName[A] match {
      case Some(encoded) =>
        // An explicit name replaces the derived one wholesale, type arguments included.
        val literal = Expr(encoded)
        Expr.quote(SName(Expr.splice(literal), Nil))
      case None =>
        // `plainPrint`, not `prettyPrint`: the latter embeds ANSI escapes, which must never reach a string literal
        // in generated code. It supplies the type arguments; the base name comes from core -- see
        // `SchemaUtils.sName`.
        val printed = Expr(Type[A].plainPrint)
        Expr.quote(SchemaUtils.sName(SNameMacros.typeFullName[A], Expr.splice(printed)))
    }
  }

  private def collectAnnotationsExpr(annotations: List[UntypedExpr]): Expr[List[Any]] = {
    implicit val AnyT: Type[Any] = STypes.AnyT
    implicit val ListAnyT: Type[List[Any]] = STypes.ListAnyT
    annotations.foldRight(Expr.quote(List.empty[Any])) { (annotation, tail) =>
      val typed: Expr[Any] = annotation.asTyped[Any]
      Expr.quote(Expr.splice(typed) :: Expr.splice(tail))
    }
  }

  protected def typeAnnotationsExpr[A: Type]: Expr[List[Any]] =
    collectAnnotationsExpr(allTypeAnnotations[A])
}
