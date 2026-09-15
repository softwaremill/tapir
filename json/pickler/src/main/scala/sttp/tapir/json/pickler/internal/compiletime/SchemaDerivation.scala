package sttp.tapir.json.pickler.internal.compiletime

import hearth.MacroCommons
import hearth.fp.effect.*
import hearth.std.*
import sttp.tapir.Schema
import sttp.tapir.Schema.SName
import sttp.tapir.SchemaType.SProductField
import sttp.tapir.json.pickler.PicklerConfiguration
import sttp.tapir.json.pickler.internal.runtime.SchemaUtils

/** Derivation of the tapir [[Schema]] half of a `Pickler`.
  *
  * The shapes produced here (names, field order, where the discriminator field goes, which annotations are folded) are pinned by
  * `SchemaDerivationTest`, and match what tapir core's `Schema.derived` produces for the same types.
  *
  * ==Design==
  * Every name (field names, type names, discriminator and enumeration values) is computed at expansion time by [[NameSupport]] from the
  * folded `PicklerConfiguration` and spliced as a literal — the same literal the codec half hands to `JsonCodecMaker`. The structure of the
  * schema is built by [[SchemaUtils]] at runtime, so that the macro reifies as little as possible.
  */
trait SchemaDerivation {
  this: MacroCommons & StdExtensions & TypeShape & AnnotationSupport & NameSupport & ImplicitPicklerSupport & PlatformSupport =>

  /** Centralised `Type.of` instances — see the note on `PicklerMacrosImpl.PTypes` for why these are not `implicit val`s at their use sites.
    */
  private[compiletime] object STypes {
    def SchemaOf[A: Type]: Type[Schema[A]] = Type.of[Schema[A]]
    def SchemaAny: Type[Schema[Any]] = Type.of[Schema[Any]]
    def ProductFieldOf[A: Type]: Type[SProductField[A]] = Type.of[SProductField[A]]
    lazy val SNameT: Type[SName] = Type.of[SName]
    lazy val StringT: Type[String] = Type.of[String]
    lazy val AnyT: Type[Any] = Type.of[Any]
    lazy val ListAnyT: Type[List[Any]] = Type.of[List[Any]]
    lazy val ListStringT: Type[List[String]] = Type.of[List[String]]
    def ListOf[A: Type]: Type[List[A]] = Type.of[List[A]]
    lazy val SchemaAnyPair: Type[(Schema[Any], String)] = Type.of[(Schema[Any], String)]
    lazy val SchemaAnyPairs: Type[List[(Schema[Any], String)]] = Type.of[List[(Schema[Any], String)]]
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
      config: PicklerConfiguration,
      cache: MLocal[ValDefsCache],
      inProgress: MLocal[Set[String]]
  ) {
    def cacheKey: String = SchemaDerivation.this.cacheKey(tpe)

    def nest[B: Type]: SchemaCtx[B] = SchemaCtx(Type[B], config, cache, inProgress)
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
        // A user-supplied `Pickler[A]` beats everything structural: it is the override mechanism, and the codec chain
        // honours the same instance, so schema and JSON stay in step. Safe to summon early only because of the
        // re-entrancy guard described in `ImplicitPicklerSupport`.
        UseUserPicklerRule,
        StructuralRule
      )(_[A]).flatMap {
        case Right(result) => MIO.pure(result)
        case Left(reasons) =>
          val explanation = reasons.toListMap.view.map { case (rule, why) =>
            if (why.isEmpty) s"  - ${rule.name}: not applicable"
            else s"  - ${rule.name}: ${why.mkString("; ")}"
          }.toList
          failSchema(PicklerDerivationError.UnsupportedType(Type[A].plainPrint, explanation))
      }
    }

  private def failSchema[T](error: PicklerDerivationError): MIO[T] = Log.error(error.message) >> MIO.fail(error)

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

  /** The structural rule: dispatch on the [[Shape]] shared with `CodecDerivation`, so that both halves take a type apart identically.
    *
    * Only the shapes that are not structural (`BuiltInScalar`, the Java numbers, `Opaque`) reach the implicit search for a `Schema[A]`.
    * That is where primitives, `String`, `java.time` types and any user-provided schema for a non-structural type are resolved —
    * `Schema.schemaForInt` and friends live on the `Schema` companion and need no import. Keeping the implicit search away from the
    * structural shapes is load-bearing: with `sttp.tapir.generic.auto.*` in scope, `Schema.derivedSchema` would resolve every case class
    * through magnolia instead of through our rules (which name types and fold annotations differently), and summoning tapir's own
    * `Schema[Option[A]]` would make the *compiler* search for `Schema[A]`, out of our control. (Hearth's `summonExprIgnoring`, the direct
    * fix, needs Scala 3.7+.)
    *
    * The cost is that a user-supplied `given Schema[MyCaseClass]` does not override structural derivation: overriding is done by supplying
    * a `Pickler`, not a `Schema`, so that the codec follows suit.
    */
  private object StructuralRule extends SchemaRule("derive from the type's shape") {
    def apply[A: SchemaCtx]: MIO[Rule.Applicability[Expr[Schema[A]]]] = {
      val shape = classify[A]
      Log.info(s"${Type[A].prettyPrint} classified as ${shape.getClass.getSimpleName}") >> (shape match {
        case Shape.BuiltInScalar() | Shape.JavaBigDecimal() | Shape.JavaBigInteger() | Shape.Opaque() => useImplicitSchema[A]
        // tapir core declares no `Schema[Char]`, but jsoniter writes a `Char` as a one-character string. Supplying the
        // matching `SString` here is what keeps the schema and the codec in agreement.
        case Shape.CharScalar()           => MIO.pure(Expr.quote(SchemaUtils.stringLikeSchema[A]))
        case Shape.ValueClass(inner)      => deriveValueClassSchema[A](inner)
        case Shape.OptionOf(element)      => deriveOptionSchema[A](element)
        case Shape.NestedOption(inner, _) => deriveOptionSchema[A](inner)
        case Shape.EitherOf(left, right)  => deriveEitherSchema[A](left, right)
        case Shape.StringMap(value)       => deriveMapSchema[A](value)
        case Shape.NonStringMap(key, _)   => failSchema(PicklerDerivationError.NonStringMapKey(key.Underlying.plainPrint))
        case Shape.Collection(element)    => deriveCollectionSchema[A](element)
        case Shape.Tuple()                => failSchema(PicklerDerivationError.TupleNotSupported(Type[A].plainPrint))
        case Shape.Singleton()            => guardingRecursion[A, Expr[Schema[A]]](deriveSingletonSchema[A])
        case Shape.Product(_, params)     => guardingRecursion[A, Expr[Schema[A]]](deriveCaseClassSchema[A](params))
        case Shape.Enumeration(_, leaves) => guardingRecursion[A, Expr[Schema[A]]](deriveStringEnumSchema[A](leaves))
        case Shape.Coproduct(_, leaves)   => guardingRecursion[A, Expr[Schema[A]]](deriveCoproductSchema[A](leaves))
      }).map(Rule.matched)
    }
  }

  private def useImplicitSchema[A: SchemaCtx]: MIO[Expr[Schema[A]]] = {
    implicit val SchemaA: Type[Schema[A]] = STypes.SchemaOf[A]
    Expr.summonImplicit[Schema[A]].toOption match {
      case Some(expr) => MIO.pure(expr)
      case None       =>
        failSchema(PicklerDerivationError.UnsupportedType(Type[A].plainPrint, List(s"no implicit Schema[${Type[A].plainPrint}] in scope")))
    }
  }

  /** An `AnyVal` wrapper is documented as its inner type, because that is how jsoniter writes it (unwrapped). Matches tapir core's own
    * `Schema.derived` for value classes.
    */
  private def deriveValueClassSchema[A: SchemaCtx](inner: ??): MIO[Expr[Schema[A]]] = {
    import inner.Underlying as Inner
    deriveSchemaFor[Inner](using sctx.nest[Inner]).map(schema => Expr.quote(Expr.splice(schema).asInstanceOf[Schema[A]]))
  }

  private def deriveOptionSchema[A: SchemaCtx](element: ??): MIO[Expr[Schema[A]]] = {
    import element.Underlying as Element
    deriveSchemaFor[Element](using sctx.nest[Element]).map { schema =>
      Expr.quote(SchemaUtils.optionSchema[Element](Expr.splice(schema)).asInstanceOf[Schema[A]])
    }
  }

  /** tapir core documents `Either` as an *untagged* coproduct of the two sides (`Schema.schemaForEither`), and the codec writes the bare
    * side value to match.
    */
  private def deriveEitherSchema[A: SchemaCtx](left: ??, right: ??): MIO[Expr[Schema[A]]] = {
    import left.Underlying as L
    import right.Underlying as R
    for {
      l <- deriveSchemaFor[L](using sctx.nest[L])
      r <- deriveSchemaFor[R](using sctx.nest[R])
    } yield Expr.quote(SchemaUtils.eitherSchema[L, R](Expr.splice(l), Expr.splice(r)).asInstanceOf[Schema[A]])
  }

  private def deriveCollectionSchema[A: SchemaCtx](element: ??): MIO[Expr[Schema[A]]] = {
    import element.Underlying as Element
    deriveSchemaFor[Element](using sctx.nest[Element]).map { schema =>
      Expr.quote(SchemaUtils.collectionSchema[Element](Expr.splice(schema)).asInstanceOf[Schema[A]])
    }
  }

  private def deriveMapSchema[A: SchemaCtx](value: ??): MIO[Expr[Schema[A]]] = {
    import value.Underlying as Value
    implicit val StringT: Type[String] = STypes.StringT
    // tapir's own convention (`SchemaMacros.generateSchemaForMap`): a `String` key contributes nothing to the name,
    // and the value's type arguments are flattened in after it.
    val typeParams = Expr(SchemaUtils.flattenTypeName(Type[Value].plainPrint))
    deriveSchemaFor[Value](using sctx.nest[Value]).map { schema =>
      Expr.quote(SchemaUtils.mapSchema[Value](Expr.splice(schema), Expr.splice(typeParams)).asInstanceOf[Schema[A]])
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

  private def deriveCaseClassSchema[A: SchemaCtx](params: List[(String, Parameter)]): MIO[Expr[Schema[A]]] = {
    implicit val SchemaA: Type[Schema[A]] = STypes.SchemaOf[A]
    implicit val SNameT: Type[SName] = STypes.SNameT
    implicit val FieldT: Type[SProductField[A]] = STypes.ProductFieldOf[A]

    val name = sNameExpr[A]
    val annotations = typeAnnotationsExpr[A]

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

    encodedFieldName[A](param, fieldName, sctx.config) match {
      case Left(error)    => failSchema(error)
      case Right(encoded) =>
        val encodedName = Expr(encoded)
        deriveSchemaFor[Field](using sctx.nest[Field]).map { fieldSchema =>
          Expr.quote {
            SchemaUtils.productField[A](
              Expr.splice(scalaName),
              Expr.splice(encodedName),
              Expr.splice(fieldSchema).asInstanceOf[Schema[Any]],
              Expr.splice(index),
              Expr.splice(annotations)
            )
          }
        }
    }
  }

  /** A sealed hierarchy with at least one non-singleton leaf: a discriminated coproduct over the **leaves** (`Shape.Coproduct`), each leaf
    * paired with the discriminator value the codec writes for it.
    */
  private def deriveCoproductSchema[A: SchemaCtx](leaves: List[(String, ??<:[A])]): MIO[Expr[Schema[A]]] = {
    implicit val SchemaA: Type[Schema[A]] = STypes.SchemaOf[A]
    implicit val SchemaAnyT: Type[Schema[Any]] = STypes.SchemaAny
    implicit val SNameT: Type[SName] = STypes.SNameT
    implicit val StringT: Type[String] = STypes.StringT
    implicit val PairT: Type[(Schema[Any], String)] = STypes.SchemaAnyPair
    implicit val PairsT: Type[List[(Schema[Any], String)]] = STypes.SchemaAnyPairs

    val name = sNameExpr[A]
    val annotations = typeAnnotationsExpr[A]
    val discriminatorField = Expr(sctx.config.discriminator)

    leaves
      .foldLeft(MIO.pure(List.empty[Expr[(Schema[Any], String)]])) { case (acc, (_, leaf)) =>
        acc.flatMap { pairs =>
          import leaf.Underlying as Leaf
          discriminatorValue[Leaf](sctx.config) match {
            case Left(error)  => failSchema(error)
            case Right(value) =>
              val valueExpr = Expr(value)
              deriveSchemaFor[Leaf](using sctx.nest[Leaf]).map { leafSchema =>
                pairs :+ Expr.quote((Expr.splice(leafSchema).asInstanceOf[Schema[Any]], Expr.splice(valueExpr)))
              }
          }
        }
      }
      .flatMap { pairs =>
        val subtypesWithValues = pairs.foldRight(Expr.quote(Nil: List[(Schema[Any], String)])) { (pair, tail) =>
          Expr.quote(Expr.splice(pair) :: Expr.splice(tail))
        }
        setCachedAndGet[A](
          sctx.cache,
          Expr.quote {
            SchemaUtils.enrichSchema[A](
              SchemaUtils.coproductSchema[A](Expr.splice(name), Expr.splice(subtypesWithValues), Expr.splice(discriminatorField)),
              Expr.splice(annotations)
            )
          }
        )
      }
  }

  /** An all-singleton hierarchy (`Shape.Enumeration`): `SString` plus a `Validator.enumeration` of the singleton values — the schema
    * counterpart of encoding it as a bare string.
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
    // The same literal the codec's leaf-name mapper produces (`NameSupport.enumerationValue`).
    val encodedNames = children.foldRight(Expr.quote(Nil: List[String])) { case ((_, child), tail) =>
      import child.Underlying as Child
      val childName = Expr(enumerationValue[Child])
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

  /** `NameSupport.sNameOf[A]`, reified: the same `SName` the codec half feeds to `toDiscriminatorValue`. */
  protected def sNameExpr[A: Type]: Expr[SName] = {
    implicit val SNameT: Type[SName] = STypes.SNameT
    implicit val StringT: Type[String] = STypes.StringT
    implicit val ListStringT: Type[List[String]] = STypes.ListStringT
    val name = sNameOf[A]
    val fullName = Expr(name.fullName)
    val typeParameters = Expr(name.typeParameterShortNames)
    Expr.quote(SName(Expr.splice(fullName), Expr.splice(typeParameters)))
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
