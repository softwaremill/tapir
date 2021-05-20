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

  def oneOfUsingField[E, V](extractor: E => V, asString: V => String)(mapping: (V, Schema[_])*)(implicit conf: Configuration): Schema[E] = ??? //TODO
}

object SchemaCompanionMacros {
  import sttp.tapir.SchemaType.*

  def generateSchemaForMap[V: Type](schemaForV: Expr[Schema[V]])(using q: Quotes): Expr[Schema[Map[String, V]]] = {
    import quotes.reflect.*
    
    def extractTypeArguments(tpe: TypeRepr): List[String] = {
      def allTypeArguments(tr: TypeRepr): Seq[TypeRepr] = tr match {
        case at: AppliedType => at.args.flatMap(tn2 => tn2 +: allTypeArguments(tn2))
        case _ => List.empty[TypeRepr]
      }
      
      allTypeArguments(tpe).map(_.typeSymbol.name).toList
    }

    val tpe = TypeRepr.of[V]
    val genericTypeParametersM = List(tpe.typeSymbol.name) ++ extractTypeArguments(tpe)

    '{Schema(SOpenProduct[Map[String, V], V](SObjectInfo("Map", ${Expr(genericTypeParametersM)}), ${schemaForV})(identity))}
  }

}