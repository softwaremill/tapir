package sttp.tapir.macros

import sttp.tapir.{Validator, Schema, SchemaType}
import sttp.tapir.SchemaType.SchemaWithValue
import sttp.tapir.generic.Configuration
import sttp.tapir.internal.SchemaMagnoliaDerivation
import magnolia1._

import scala.quoted.*

trait SchemaMacros[T] { this: Schema[T] =>

  /** Modifies nested schemas for case classes and case class families (sealed traits / enums), accessible with `path`, using the given
    * `modification` function. To traverse collections, use `.each`.
    */
  inline def modify[U](inline path: T => U)(inline modification: Schema[U] => Schema[U]): Schema[T] = ${
    SchemaMacros.modifyImpl[T, U]('this)('path)('modification)
  }
}

object SchemaMacros {
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
            case elements => report.throwError(s"Invalid use of path elements [${elements.mkString(", ")}]. $ShapeInfo, got: ${tree}")
          }

          idents.flatMap(toPath(_, newAcc))
        }

        /** The first segment from path (e.g. `_.age` -> `_`) */
        case i: Ident =>
          acc
        case t =>
          report.throwError(s"Unsupported path element $t")
      }
    }

    val pathElements = path.asTerm match {
      /** Single inlined path */
      case Inlined(_, _, Block(List(DefDef(_, _, _, Some(p))), _)) =>
        toPath(p, List.empty)
      case _ =>
        report.throwError(s"Unsupported path [$path]")
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

  inline def oneOfUsingField[E, V](inline extractor: E => V, asString: V => String)(
      mapping: (V, Schema[_])*
  )(implicit conf: Configuration): Schema[E] = ${
    SchemaCompanionMacros.generateOneOfUsingField[E, V]('extractor, 'asString)('mapping)('conf)
  }

  /** Create a schema for scala `Enumeration` and the `Validator` instance based on possible enumeration values */
  implicit inline def derivedEnumerationValue[T <: Enumeration#Value]: Schema[T] = ${
    SchemaCompanionMacros.derivedEnumerationValue[T]
  }

  /** Creates a schema for an enumeration, where the validator is derived using [[sttp.tapir.Validator.derivedEnumeration]]. This requires
    * that all subtypes of the sealed hierarchy `T` must be `object`s.
    *
    * @param encode
    *   Specify how values of this type can be encoded to a raw value (typically a [[String]]; the raw form should correspond with
    *   `schemaType`). This encoding will be used when generating documentation.
    * @param schemaType
    *   The low-level representation of the enumeration. Defaults to a string.
    */
  inline def derivedEnumeration[T](
      encode: Option[T => Any] = None,
      schemaType: SchemaType[T] = SchemaType.SString[T](),
      default: Option[T] = None
  ): Schema[T] = {
    val v0 = Validator.derivedEnumeration[T]
    val v = encode.fold(v0)(e => v0.encode(e))

    val s0 = Schema.string.validate(v)
    default.fold(s0)(d => s0.default(d, encode.map(e => e(d))))
  }
}

object SchemaCompanionMacros {

  import sttp.tapir.SchemaType.*
  import sttp.tapir.internal.SNameMacros

  def generateSchemaForMap[K: Type, V: Type](schemaForV: Expr[Schema[V]], keyToString: Expr[K => String])(using
      q: Quotes
  ): Expr[Schema[Map[K, V]]] = {

    import quotes.reflect.*

    val ktpe = TypeRepr.of[K]
    val ktpeName = ktpe.typeSymbol.name
    val vtpe = TypeRepr.of[V]

    val genericTypeParameters = (if (ktpeName == "String") Nil else List(ktpeName)) ++ SNameMacros.extractTypeArguments(ktpe) ++
      List(vtpe.typeSymbol.name) ++ SNameMacros.extractTypeArguments(vtpe)

    '{
      Schema(
        SOpenProduct[Map[K, V], V](${ schemaForV })(_.map { case (k, v) => ($keyToString(k), v) }),
        Some(Schema.SName("Map", ${ Expr(genericTypeParameters) }))
      )
    }
  }

  def generateOneOfUsingField[E: Type, V: Type](extractor: Expr[E => V], asString: Expr[V => String])(
      mapping: Expr[Seq[(V, Schema[_])]]
  )(conf: Expr[Configuration])(using q: Quotes): Expr[Schema[E]] = {
    import q.reflect.*

    def resolveFunctionName(f: Statement): String = f match {
      case Inlined(_, _, block)        => resolveFunctionName(block)
      case Block(List(), block)        => resolveFunctionName(block)
      case Block(List(defdef), _)      => resolveFunctionName(defdef)
      case DefDef(_, _, _, Some(body)) => resolveFunctionName(body)
      case Apply(fun, _)               => resolveFunctionName(fun)
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
      import _root_.scala.collection.immutable.{List, Map}

      val mappingAsList = $mapping.toList
      val mappingAsMap = mappingAsList.toMap
      val discriminator = SDiscriminator(
        _root_.sttp.tapir.FieldName(${ Expr(functionName) }, $conf.toEncodedName(${ Expr(functionName) })),
        mappingAsMap.collect { case (k, sf @ Schema(_, Some(fname), _, _, _, _, _, _, _, _, _)) =>
          $asString.apply(k) -> SRef(fname)
        }
      )
      val sname = SName(SNameMacros.typeFullName[E], ${ Expr(typeParams) })
      val subtypes = mappingAsList.map(_._2)
      Schema(
        SCoproduct[E](subtypes, _root_.scala.Some(discriminator)) { e =>
          val ee = $extractor(e)
          mappingAsMap.get(ee).map(s => SchemaWithValue(s.asInstanceOf[Schema[Any]], e))
        },
        Some(sname)
      )
    }
  }

  def derivedEnumerationValue[T: Type](using q: Quotes): Expr[Schema[T]] = {
    import q.reflect.*
    import sttp.tapir.internal.SchemaAnnotations

    val Enumeration = TypeTree.of[scala.Enumeration].tpe

    val tpe = TypeRepr.of[T]

    val owner = tpe.typeSymbol.owner.tree

    if (owner.symbol != Enumeration.typeSymbol) {
      report.errorAndAbort("Can only derive Schema for values owned by scala.Enumeration")
    } else {

      val enumerationPath = tpe.show.split("\\.").dropRight(1).mkString(".")
      val enumeration = Symbol.requiredModule(enumerationPath)

      val sName = '{ Some(Schema.SName(${ Expr(enumerationPath) })) }

      '{
        SchemaAnnotations
          .derived[T]
          .enrich(
            Schema
              .string[T]
              .validate(
                Validator.enumeration(
                  ${ Ref(enumeration).asExprOf[scala.Enumeration] }.values.toList.asInstanceOf[List[T]],
                  v => Option(v),
                  $sName
                )
              )
          )
      }
    }
  }
}
