package sttp.tapir.macros

import sttp.tapir.{Schema, SchemaAnnotations, SchemaType, Validator}
import sttp.tapir.generic.Configuration
import sttp.tapir.Schema.SName
import sttp.tapir.generic.auto.SchemaMagnoliaDerivation

import scala.quoted.*

trait SchemaMacros[T] { this: Schema[T] =>

  /** Modifies nested schemas for case classes and case class families (sealed traits / enums), accessible with `path`, using the given
    * `modification` function. To traverse collections, use `.each`.
    *
    * Should only be used if the schema hasn't been created by `.map` ping another one. In such a case, the shape of the schema doesn't
    * correspond to the type `T`, but to some lower-level representation of the type.
    *
    * If the shape of the schema doesn't correspond to the path, the schema remains unchanged.
    */
  inline def modify[U](inline path: T => U)(inline modification: Schema[U] => Schema[U]): Schema[T] = ${
    SchemaMacros.modifyImpl[T, U]('this)('path)('modification)
  }
}

private[tapir] object SchemaMacros {
  private val ShapeInfo = "Path must have shape: _.field1.field2.each.field3.(...)"

  def modifyImpl[T: Type, U: Type](
      base: Expr[Schema[T]]
  )(path: Expr[T => U])(modification: Expr[Schema[U] => Schema[U]])(using Quotes): Expr[Schema[T]] = {
    import quotes.reflect.*

    enum PathElement {
      case TermPathElement(term: String, xargs: String*) extends PathElement
      case FunctorPathElement(functor: String, method: String, xargs: String*) extends PathElement
    }

    def toPath(tree: Tree, acc: List[PathElement]): Seq[PathElement] = {
      def typeSupported(modifyType: String) =
        Seq("ModifyEach", "ModifyEither", "ModifyEachMap")
          .exists(modifyType.endsWith)

      tree match {

        /** Field access */
        case Select(deep, ident) =>
          toPath(deep, PathElement.TermPathElement(ident) :: acc)

        /** Method call with no arguments and using clause */
        case Apply(Apply(TypeApply(Ident(f), _), idents), _) if typeSupported(f) => {
          val newAcc = acc match {

            /** replace the term controlled by quicklens */
            case PathElement.TermPathElement(term, xargs @ _*) :: rest => PathElement.FunctorPathElement(f, term, xargs: _*) :: rest
            case elements => report.errorAndAbort(s"Invalid use of path elements [${elements.mkString(", ")}]. $ShapeInfo, got: ${tree}")
          }

          idents.flatMap(toPath(_, newAcc))
        }

        /** The first segment from path (e.g. `_.age` -> `_`) */
        case i: Ident =>
          acc
        case t =>
          report.errorAndAbort(s"Unsupported path element $t")
      }
    }

    val pathElements = path.asTerm match {

      /** Single inlined path */
      case Inlined(_, _, Block(List(DefDef(_, _, _, Some(p))), _)) =>
        toPath(p, List.empty)
      case _ =>
        report.errorAndAbort(s"Unsupported path [$path]")
    }

    '{
      val pathValue = ${
        Expr(pathElements.map {
          case PathElement.TermPathElement(c)                   => c
          case PathElement.FunctorPathElement(_, method, _ @_*) => method
        })
      }

      $base.modifyUnsafe(pathValue: _*)($modification)
    }
  }
}

trait SchemaCompanionMacros extends SchemaMagnoliaDerivation {
  implicit inline def schemaForMap[V: Schema]: Schema[Map[String, V]] = ${
    SchemaCompanionMacros.generateSchemaForMap[String, V]('{ summon[Schema[V]] }, 'identity)
  }

  /** Create a schema for a map with arbitrary keys. The schema for the keys (`Schema[K]`) should be a string (that is, the schema type
    * should be [[sttp.tapir.SchemaType.SString]]), however this cannot be verified at compile-time and is not verified at run-time.
    *
    * The given `keyToString` conversion function is used during validation.
    *
    * If you'd like this schema to be available as an implicit for a given type of keys, create an custom implicit, e.g.:
    *
    * {{{
    * case class MyKey(value: String) extends AnyVal
    * implicit val schemaForMyMap = Schema.schemaForMap[MyKey, MyValue](_.value)
    * }}}
    */
  inline def schemaForMap[K, V: Schema](keyToString: K => String): Schema[Map[K, V]] = ${
    SchemaCompanionMacros.generateSchemaForMap[K, V]('{ summon[Schema[V]] }, 'keyToString)
  }

  /** Create a coproduct schema (e.g. for a `sealed trait`), where the value of the discriminator between child types is a read of a field
    * of the base type. The field, if not yet present, is added to each child schema.
    *
    * The schemas of the child types have to be provided explicitly with their value mappings in `mapping`.
    *
    * Note that if the discriminator value is some transformation of the child's type name (obtained using the implicit [[Configuration]]),
    * the coproduct schema can be derived automatically or semi-automatically.
    *
    * @param discriminatorSchema
    *   The schema that is used when adding the discriminator as a field to child schemas (if it's not yet in the schema).
    */
  inline def oneOfUsingField[E, V](inline extractor: E => V, asString: V => String)(
      mapping: (V, Schema[_])*
  )(implicit conf: Configuration, discriminatorSchema: Schema[V]): Schema[E] = ${
    SchemaCompanionMacros.generateOneOfUsingField[E, V]('extractor, 'asString)('mapping)('conf, 'discriminatorSchema)
  }

  /** Create a coproduct schema for an `enum`, `sealed trait` or `sealed abstract class`, where to discriminate between child types a
    * wrapper product is used. The name of the sole field in this product corresponds to the type's name, transformed using the implicit
    * [[Configuration]].
    *
    * See also [[Schema.wrapWithSingleFieldProduct]], which creates the wrapper product given a schema.
    */
  inline def oneOfWrapped[E](implicit conf: Configuration): Schema[E] = ${ SchemaCompanionMacros.generateOneOfWrapped[E]('conf) }

  /** Derives the schema for a union type `E`. Schemas for all components of the union type must be available in the implicit scope at the
    * point of invocation.
    */
  inline def derivedUnion[E]: Schema[E] = ${ SchemaCompanionMacros.derivedUnion[E] }

  /** Create a schema for an [[Enumeration]], where the validator is created using the enumeration's values. The low-level representation of
    * the enum is a `String`, and the enum values in the documentation will be encoded using `.toString`.
    */
  implicit inline def derivedEnumerationValue[T <: Enumeration#Value]: Schema[T] =
    derivedEnumerationValueCustomise[T].defaultStringBased

  /** Creates a schema for an [[Enumeration]], where the validator is created using the enumeration's values. Unlike the default
    * [[derivedEnumerationValue]] method, which provides the schema implicitly, this variant allows customising how the schema is created.
    * This is useful if the low-level representation of the schema is different than a `String`, or if the enumeration's values should be
    * encoded in a different way than using `.toString`.
    *
    * Because of technical limitations of macros, the customisation arguments can't be given here directly, instead being delegated to
    * [[CreateDerivedEnumerationSchema]].
    */
  inline def derivedEnumerationValueCustomise[T <: scala.Enumeration#Value]: CreateDerivedEnumerationSchema[T] =
    new CreateDerivedEnumerationSchema(derivedEnumerationValueValidator[T], SchemaAnnotations.derived[T])

  private inline def derivedEnumerationValueValidator[T <: Enumeration#Value]: Validator.Enumeration[T] = ${
    SchemaCompanionMacros.derivedEnumerationValueValidator[T]
  }

  /** Creates a schema for an enumeration, where the validator is derived using [[sttp.tapir.Validator.derivedEnumeration]]. This requires
    * that this is an `enum`, where all cases are parameterless, or that all subtypes of the sealed hierarchy `T` are `object` s.
    *
    * This method cannot be implicit, as there's no way to constraint the type `T` to be an enum / sealed trait or class enumeration, so
    * that this would be invoked only when necessary.
    */
  inline def derivedEnumeration[T]: CreateDerivedEnumerationSchema[T] =
    new CreateDerivedEnumerationSchema(Validator.derivedEnumeration[T], SchemaAnnotations.derived[T])

  inline given derivedStringBasedUnionEnumeration[S](using IsUnionOf[String, S]): Schema[S] =
    lazy val validator = Validator.derivedStringBasedUnionEnumeration[S]
    Schema
      .string[S]
      .name(SName(validator.possibleValues.toList.mkString("_or_")))
      .validate(validator)
}

private[tapir] object SchemaCompanionMacros {

  import sttp.tapir.SchemaType.*
  import sttp.tapir.internal.SNameMacros

  def generateSchemaForMap[K: Type, V: Type](schemaForV: Expr[Schema[V]], keyToString: Expr[K => String])(using
      q: Quotes
  ): Expr[Schema[Map[K, V]]] = {

    import quotes.reflect.*

    val ktpe = TypeRepr.of[K]
    val ktpeName = SNameMacros.typeFullNameFromTpe(ktpe)
    val vtpe = TypeRepr.of[V]

    val genericTypeParameters = (if (ktpeName.split('.').lastOption.contains("String")) Nil else List(ktpeName)) ++
      SNameMacros.extractTypeArguments(ktpe) ++ List(SNameMacros.typeFullNameFromTpe(vtpe)) ++
      SNameMacros.extractTypeArguments(vtpe)

    '{
      Schema(
        SOpenProduct[Map[K, V], V](Nil, ${ schemaForV })(_.map { case (k, v) => ($keyToString(k), v) }),
        Some(Schema.SName("Map", ${ Expr(genericTypeParameters) }))
      )
    }
  }

  def generateOneOfUsingField[E: Type, V: Type](extractor: Expr[E => V], asString: Expr[V => String])(
      mapping: Expr[Seq[(V, Schema[_])]]
  )(conf: Expr[Configuration], discriminatorSchema: Expr[Schema[V]])(using q: Quotes): Expr[Schema[E]] = {
    import q.reflect.*

    def resolveFunctionName(f: Statement): String = f match {
      case Inlined(_, _, block)        => resolveFunctionName(block)
      case Block(List(), block)        => resolveFunctionName(block)
      case Block(List(defdef), _)      => resolveFunctionName(defdef)
      case DefDef(_, _, _, Some(body)) => resolveFunctionName(body)
      case Apply(fun, _)               => resolveFunctionName(fun)
      case Ident(str)                  => str
      case Select(_, kind)             => kind
    }

    val tpe = TypeRepr.of[E]

    val functionName = resolveFunctionName(extractor.asTerm)
    val typeParams = SNameMacros.extractTypeArguments(tpe)

    '{
      import _root_.sttp.tapir.internal._
      import _root_.sttp.tapir.Schema
      import _root_.sttp.tapir.Schema._
      import _root_.sttp.tapir.SchemaType._

      val mappingAsList = $mapping.toList
      val mappingAsMap = mappingAsList.toMap

      val discriminatorName = _root_.sttp.tapir.FieldName(${ Expr(functionName) }, $conf.toEncodedName(${ Expr(functionName) }))
      val discriminatorMapping = mappingAsMap.collect { case (k, sf @ Schema(_, Some(fname), _, _, _, _, _, _, _, _, _)) =>
        $asString.apply(k) -> SRef(fname)
      }

      val sname = SName(SNameMacros.typeFullName[E], ${ Expr(typeParams) })
      val subtypes = mappingAsList.map(_._2)
      Schema(
        (SCoproduct[E](subtypes, None) { e =>
          val ee = $extractor(e)
          mappingAsMap.get(ee).map(s => SchemaWithValue(s.asInstanceOf[Schema[Any]], e))
        }).addDiscriminatorField(
          discriminatorName,
          $discriminatorSchema,
          discriminatorMapping
        ),
        Some(sname)
      )
    }
  }

  def generateOneOfWrapped[E: Type](conf: Expr[Configuration])(using q: Quotes): Expr[Schema[E]] = {
    import q.reflect.*

    val tpe = TypeRepr.of[E]
    val symbol = tpe.typeSymbol
    val typeParams = SNameMacros.extractTypeArguments(tpe)

    if (!symbol.isClassDef || !(symbol.flags is Flags.Sealed)) {
      report.errorAndAbort("Can only generate a coproduct schema for an enum, sealed trait or class.")
    } else {
      val children = symbol.children.toList.sortBy(_.name)

      val childSchemas: List[Expr[(String, Schema[_])]] = children.map(child =>
        if child.isClassDef
        then // this can be a type (enum case with params / case class with params), or a parameterless enum case / case object
          TypeIdent(child).tpe.asType match {
            case '[f] => {
              Expr.summon[Schema[f]] match {
                case Some(subSchema) => '{ ${ Expr(child.name) } -> Schema.wrapWithSingleFieldProduct(${ subSchema })($conf) }
                case None => {
                  val typeName = TypeRepr.of[f].typeSymbol.name
                  report.errorAndAbort(s"Cannot summon schema for `${typeName}`. Make sure schema derivation is properly configured.")
                }
              }
            }
          }
        else '{ ${ Expr(child.name) } -> Schema(SchemaType.SProduct[E](Nil), name = Some(Schema.SName(${ Expr(child.name) }))) }
      )

      def subtypeSchema(e: Expr[E], map: Expr[Map[String, Schema[_]]]) = {
        val eIdent = e.asTerm match {
          case Inlined(_, _, ei: Ident) => ei
          case ei: Ident                => ei
        }

        val t = Match(
          eIdent,
          children.map { child =>
            val caseThen = Block(Nil, '{ Some(SchemaWithValue($map(${ Expr(child.name) }).asInstanceOf[Schema[Any]], $e)) }.asTerm)
            if child.isClassDef then CaseDef(Typed(Wildcard(), TypeIdent(child)), None, caseThen)
            else CaseDef(Ident(child.termRef), None, caseThen)
          }
        )

        t.asExprOf[Option[SchemaWithValue[_]]]
      }

      '{
        import _root_.sttp.tapir.internal._
        import _root_.sttp.tapir.Schema
        import _root_.sttp.tapir.Schema._
        import _root_.sttp.tapir.SchemaType._
        import _root_.scala.collection.immutable.{List, Map}

        val subclassNameToSchema: List[(String, Schema[_])] = List(${ Varargs(childSchemas) }: _*)
        val subclassNameToSchemaMap: Map[String, Schema[_]] = subclassNameToSchema.toMap

        val sname = SName(SNameMacros.typeFullName[E], ${ Expr(typeParams) })
        Schema(
          schemaType = SCoproduct[E](subclassNameToSchema.map(_._2), None) { e =>
            ${ subtypeSchema('e, 'subclassNameToSchemaMap) }
          },
          name = Some(sname)
        )
      }
    }
  }

  def derivedEnumerationValueValidator[T: Type](using q: Quotes): Expr[Validator.Enumeration[T]] = {
    import q.reflect.*

    val tpe = TypeRepr.of[T]

    if (tpe <:< TypeRepr.of[Enumeration#Value]) {
      val enumerationPath = tpe.show.split("\\.").dropRight(1).mkString(".")
      val enumeration = Symbol.requiredModule(enumerationPath)

      val sName = '{
        Some(Schema.SName(${
          Expr(enumerationPath)
        }))
      }

      '{
        Validator.enumeration(
          ${ Ref(enumeration).asExprOf[scala.Enumeration] }.values.toList.asInstanceOf[List[T]],
          v => Option(v),
          $sName
        )
      }
    } else {
      report.errorAndAbort(s"Can only derive Schema for values owned by scala.Enumeration")
    }
  }

  def derivedUnion[T: Type](using q: Quotes): Expr[Schema[T]] = {
    import q.reflect.*

    val tpe = TypeRepr.of[T]
    def typeParams = SNameMacros.extractTypeArguments(tpe)

    // first, finding all of the components of the union type
    def findOrTypes(t: TypeRepr, failIfNotOrType: Boolean = true): List[TypeRepr] =
      t.dealias match {
        // only failing if the top-level type is not an OrType
        case OrType(l, r) => findOrTypes(l, failIfNotOrType = false) ++ findOrTypes(r, failIfNotOrType = false)
        case _ if failIfNotOrType =>
          report.errorAndAbort(s"Can only derive Schemas for union types, got: ${tpe.show}")
        case _ => List(t)
      }

    val orTypes = findOrTypes(tpe)

    // then, looking up schemas for each of the components
    val schemas: List[Expr[Schema[_]]] = orTypes.map { orType =>
      orType.asType match {
        case '[f] =>
          Expr.summon[Schema[f]] match {
            case Some(subSchema) => subSchema
            case None =>
              val typeName = TypeRepr.of[f].show
              report.errorAndAbort(s"Cannot summon schema for `$typeName`. Make sure schema derivation is properly configured.")
          }
      }
    }

    // then, constructing the name of the schema; if the type is not named, we generate a name by hand by concatenating
    // names of the components
    val orTypesNames = Expr.ofList(orTypes.map { orType =>
      orType.asType match {
        case '[f] =>
          val typeParams = SNameMacros.extractTypeArguments(orType)
          '{ _root_.sttp.tapir.Schema.SName(SNameMacros.typeFullName[f], ${ Expr(typeParams) }) }
      }
    })

    val baseName = SNameMacros.typeFullNameFromTpe(tpe)
    val snameExpr = if baseName.isEmpty then '{ SName(${ orTypesNames }.map(_.show).mkString("_or_")) }
    else '{ SName(${ Expr(baseName) }, ${ Expr(typeParams) }) }

    // then, generating the method which maps a specific value to a schema, trying to match to one of the components
    val typesAndSchemas = orTypes.zip(schemas) // both lists have the same length
    def subtypeSchema(e: Expr[T]) = {
      val eIdent = e.asTerm match {
        case Inlined(_, _, ei: Ident) => ei
        case ei: Ident                => ei
      }

      // if an or-type component that is generic appears more than once, we won't be able to perform a runtime check,
      // to get the correct schema; in such case, instead of generating a `case ...`, we add a (single!)
      // `case _ => None` to the match
      val genericTypesThatAppearMoreThanOnce = {
        var seen = Set[String]()
        var result = Set[String]()

        orTypes.foreach { orType =>
          orType.classSymbol match {
            case Some(sym) if orType.typeArgs.nonEmpty => // is generic
              if seen.contains(sym.fullName) then result = result + sym.fullName
              else seen = seen + sym.fullName
            case _ => // skip
          }
        }

        result
      }

      val baseCases = typesAndSchemas.flatMap { (orType, orTypeSchema) =>
        def caseThen = Block(Nil, '{ Some(SchemaWithValue($orTypeSchema.asInstanceOf[Schema[Any]], $e)) }.asTerm)

        orType.classSymbol match
          case None => Some(CaseDef(Ident(orType.termSymbol.termRef), None, caseThen))
          case Some(sym) if orType.typeArgs.nonEmpty =>
            if genericTypesThatAppearMoreThanOnce.contains(sym.fullName) then None
            else
              val wildcardTypeParameters: List[Tree] =
                List.fill(orType.typeArgs.length)(TypeBoundsTree(TypeTree.of[Nothing], TypeTree.of[Any]))
              Some(CaseDef(Typed(Wildcard(), Applied(TypeIdent(sym), wildcardTypeParameters)), None, caseThen))
          case Some(sym) => Some(CaseDef(Typed(Wildcard(), TypeIdent(sym)), None, caseThen))
      }
      val cases =
        if genericTypesThatAppearMoreThanOnce.nonEmpty
        then baseCases :+ CaseDef(Wildcard(), None, Block(Nil, '{ None }.asTerm))
        else baseCases
      val t = Match(eIdent, cases)

      t.asExprOf[Option[SchemaWithValue[_]]]
    }

    // finally, generating code which creates the SCoproduct
    '{
      import _root_.sttp.tapir.Schema
      import _root_.sttp.tapir.Schema._
      import _root_.sttp.tapir.SchemaType._
      import _root_.scala.collection.immutable.List

      val childSchemas = List(${ Varargs(schemas) }: _*)
      val sname = $snameExpr

      Schema(
        schemaType = SCoproduct[T](childSchemas, None) { e => ${ subtypeSchema('{ e }) } },
        name = Some(sname)
      )
    }
  }
}
