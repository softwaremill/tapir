package sttp.tapir.internal

import sttp.tapir.Schema

import scala.annotation.tailrec
import scala.reflect.macros.blackbox

private[tapir] object ModifySchemaMacro {
  private val ShapeInfo = "Path must have shape: _.field1.field2.each.field3.(...)"

  def generateModify[T: c.WeakTypeTag, U: c.WeakTypeTag](
      c: blackbox.Context
  )(path: c.Expr[T => U])(modification: c.Expr[Schema[U] => Schema[U]]): c.Tree =
    applyModification[T, U](c)(extractPathFromFunctionCall(c)(path), modification)

  private def applyModification[T: c.WeakTypeTag, U: c.WeakTypeTag](c: blackbox.Context)(
      path: c.Expr[List[String]],
      modification: c.Expr[Schema[U] => Schema[U]]
  ): c.Tree = {
    import c.universe._
    q"""{
      ${c.prefix}.modifyUnsafe($path:_*)($modification)
     }"""
  }

  /** Converts path to list of strings */
  private def extractPathFromFunctionCall[T: c.WeakTypeTag, U: c.WeakTypeTag](c: blackbox.Context)(
      path: c.Expr[T => U]
  ): c.Expr[List[String]] = {
    import c.universe._

    sealed trait PathElement
    case class TermPathElement(term: c.TermName, xargs: c.Tree*) extends PathElement
    case class FunctorPathElement(functor: c.Tree, method: c.TermName, xargs: c.Tree*) extends PathElement

    /** _.a.b.each.c => List(TPE(a), TPE(b), FPE(functor, each/at/eachWhere, xargs), TPE(c))
      */
    @tailrec
    def collectPathElements(tree: c.Tree, acc: List[PathElement]): List[PathElement] = {
      def typeSupported(quicklensType: c.Tree) =
        Seq("ModifyEach", "ModifyEither", "ModifyEachMap")
          .exists(quicklensType.toString.endsWith)

      tree match {
        case q"$parent.$child " =>
          collectPathElements(parent, TermPathElement(child) :: acc)
        case q"$tpname[..$_]($t)($f) " if typeSupported(tpname) =>
          val newAcc = acc match {
            // replace the term controlled by quicklens
            case TermPathElement(term, xargs @ _*) :: rest => FunctorPathElement(f, term, xargs: _*) :: rest
            case elements                                  =>
              c.abort(c.enclosingPosition, s"Invalid use of path elements [${elements.mkString(", ")}]. $ShapeInfo, got: ${path.tree}")
          }
          collectPathElements(t, newAcc)
        case _: Ident => acc
        case _        => c.abort(c.enclosingPosition, s"Unsupported path element. $ShapeInfo, got: $tree")
      }
    }

    val pathEls = path.tree match {
      case q"($arg) => $pathBody " => collectPathElements(pathBody, Nil)
      case _                       => c.abort(c.enclosingPosition, s"$ShapeInfo, got: ${path.tree}")
    }

    c.Expr[List[String]](q"${pathEls.collect {
        case TermPathElement(c)                         => c.decodedName.toString
        case FunctorPathElement(functor, method, _ @_*) =>
          method.decodedName.toString
      }}")
  }
}
