package sttp.tapir.typelevel

import scala.quoted.Quotes

import scala.quoted.*

trait UnionTypeMirror[A] {

  type ElementTypes <: NonEmptyTuple
  // Number of elements in the union
  def size: Int
}

// Building a class is more convenient to instantiate using macros
class UnionTypeMirrorImpl[A, T <: NonEmptyTuple](val size: Int) extends UnionTypeMirror[A] {

  override type ElementTypes = T
}

object UnionTypeMirror {
  transparent inline given derived[A]: UnionTypeMirror[A] = ${ derivedImpl[A] }

  private def derivedImpl[A](using Quotes, Type[A]): Expr[UnionTypeMirror[A]] = {
    import quotes.reflect.*

    val tplPrependType = TypeRepr.of[? *: ?] match
      case AppliedType(tycon, _) => tycon
    val tplConcatType = TypeRepr.of[Tuple.Concat]

    def prependTypes(head: TypeRepr, tail: TypeRepr): TypeRepr =
      AppliedType(tplPrependType, List(head, tail))

    def concatTypes(left: TypeRepr, right: TypeRepr): TypeRepr =
      AppliedType(tplConcatType, List(left, right))

    def rec(tpe: TypeRepr): (Int, TypeRepr) =
      tpe.dealias match {
        case OrType(left, right) =>
          val (c1, rec1) = rec(left)
          val (c2, rec2) = rec(right)
          (c1 + c2, concatTypes(rec1, rec2))
        case t => (1, prependTypes(t, TypeRepr.of[EmptyTuple]))
      }
    val (size, tupled) =
      TypeRepr.of[A].dealias match {
        case or: OrType =>
          val (s, r) = rec(or)
          (s, r.asType.asInstanceOf[Type[Elems]])
        case tpe => report.errorAndAbort(s"${tpe.show} is not a union type")
      }

    type Elems

    given Type[Elems] = tupled

    Apply( // Passing the type using quotations causes the type to not be inlined
      TypeApply(
        Select.unique(
          New(
            Applied(
              TypeTree.of[UnionTypeMirrorImpl],
              List(
                TypeTree.of[A],
                TypeTree.of[Elems]
              )
            )
          ),
          "<init>"
        ),
        List(
          TypeTree.of[A],
          TypeTree.of[Elems]
        )
      ),
      List(Literal(IntConstant((size))))
    ).asExprOf[UnionTypeMirror[A]]
  }
}
