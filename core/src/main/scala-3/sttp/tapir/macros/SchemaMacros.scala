package sttp.tapir.macros

import sttp.tapir.Schema
import sttp.tapir.generic.Configuration
import sttp.tapir.internal.SchemaMagnoliaDerivation
import magnolia._

import scala.quoted.*

trait SchemaMacros[T] { this: Schema[T] =>
  inline def modify[U](inline path: T => U)(inline modification: Schema[U] => Schema[U]): Schema[T] = ${ SchemaMacros.modifyImpl[T, U]('this)('path)('modification)}
}

object SchemaMacros {
  private val ShapeInfo = "Path must have shape: _.field1.field2.each.field3.(...)"

  def modifyImpl[T: Type, U: Type](base: Expr[Schema[T]])(path: Expr[T => U])(modification: Expr[Schema[U] => Schema[U]])(using Quotes): Expr[Schema[T]] = {
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
      val pathValue = ${ Expr(pathElements.map {
        case PathElement.TermPathElement(c) => c
        case PathElement.FunctorPathElement(_, method, _ @_*) => method
      }) }

      $base.modifyUnsafe(pathValue: _*)($modification)
    }
  }
}

trait SchemaCompanionMacros extends SchemaMagnoliaDerivation {
  implicit inline def schemaForMap[V: Schema]: Schema[Map[String, V]] = ${ SchemaCompanionMacros.generateSchemaForMap[V]('{ summon[Schema[V]] }) }

  inline def oneOfUsingField[E, V](inline extractor: E => V, asString: V => String)(mapping: (V, Schema[_])*)(implicit conf: Configuration): Schema[E] = 
  ${ SchemaCompanionMacros.generateOneOfUsingField[E, V]('extractor, 'asString)('mapping)('conf)}
}

object SchemaCompanionMacros {
  import sttp.tapir.SchemaType.*
  import sttp.tapir.internal.SObjectInfoMacros

  def generateSchemaForMap[V: Type](schemaForV: Expr[Schema[V]])(using q: Quotes): Expr[Schema[Map[String, V]]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[V]
    val genericTypeParametersM = List(tpe.typeSymbol.name) ++ SObjectInfoMacros.extractTypeArguments(tpe)

    '{Schema(SOpenProduct[Map[String, V], V](SObjectInfo("Map", ${Expr(genericTypeParametersM)}), ${schemaForV})(identity))}
  }

  def generateOneOfUsingField[E: Type, V: Type](extractor: Expr[E => V], asString: Expr[V => String])(
      mapping: Expr[Seq[(V, Schema[_])]]
  )(conf: Expr[Configuration])(using q: Quotes): Expr[Schema[E]] = {
    import q.reflect.*
   
    def resolveFunctionName(f: Statement): String = f match {
      case Inlined(_, _, block) => resolveFunctionName(block)
      case Block(List(), block) => resolveFunctionName(block)
      case Block(List(defdef), _) => resolveFunctionName(defdef)
      case DefDef(_, _,_, Some(body)) => resolveFunctionName(body)
      case Apply(fun, _) => resolveFunctionName(fun)
      case Select(_ ,kind) => kind
    }

    val tpe = TypeRepr.of[E]

    val functionName = resolveFunctionName(extractor.asTerm)
    val typeParams = SObjectInfoMacros.extractTypeArguments(tpe)

    '{
      import _root_.sttp.tapir.internal._
      import _root_.sttp.tapir.Schema
      import _root_.sttp.tapir.Schema._
      import _root_.sttp.tapir.SchemaType._
      import _root_.scala.collection.immutable.{List, Map}

      val mappingAsList = $mapping.toList
      val mappingAsMap = mappingAsList.toMap
      val discriminator = SDiscriminator(_root_.sttp.tapir.FieldName(${Expr(functionName)}, $conf.toEncodedName(${Expr(functionName)})), mappingAsMap.map { case (k, sf) => 
        $asString.apply(k) -> SRef(sf.schemaType.asInstanceOf[SObject[_]].info)
      })
      val info = SObjectInfo(SObjectInfoMacros.typeFullName[E], ${Expr(typeParams)})
      val subtypes = (mappingAsList.map(_._2).map(s => s.schemaType.asInstanceOf[SObject[_]].info -> s): List[(SObjectInfo, Schema[_])]).toListMap
      Schema(SCoproduct[E](info, subtypes, _root_.scala.Some(discriminator))(
        e => mappingAsMap.get($extractor(e)).map(_.schemaType.asInstanceOf[SObject[_]].info)
      ))
    }
  }  

}