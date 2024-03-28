package sttp.tapir.typelevel

import scala.quoted.Quotes

import scala.quoted.*

trait UnionTypeMirror[A] {

  type ElementTypes <: Tuple
}

// Building a class is more convenient to instantiate using macros
class UnionTypeMirrorImpl[A, T <: Tuple] extends UnionTypeMirror[A] {

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

    def rec(tpe: TypeRepr): TypeRepr =
      tpe.dealias match {
        case OrType(left, right) => concatTypes(rec(left), rec(right))
        case t                   => prependTypes(t, TypeRepr.of[EmptyTuple])
      }
    val tupled =
      TypeRepr.of[A].dealias match {
        case or: OrType => rec(or).asType.asInstanceOf[Type[Elems]]
        case tpe        => report.errorAndAbort(s"${tpe.show} is not a union type")
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
      Nil
    ).asExprOf[UnionTypeMirror[A]]
  }
}
