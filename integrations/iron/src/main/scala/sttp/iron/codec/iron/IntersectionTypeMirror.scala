package sttp.tapir.typelevel
import scala.quoted.Quotes

import scala.quoted.*

trait IntersectionTypeMirror[A] {

  type ElementTypes <: Tuple
}

// Building a class is more convenient to instantiate using macros
class IntersectionTypeMirrorImpl[A, T <: Tuple] extends IntersectionTypeMirror[A] {
  override type ElementTypes = T
}

object IntersectionTypeMirror {

  transparent inline given derived[A]: IntersectionTypeMirror[A] = ${ derivedImpl[A] }

  private def derivedImpl[A](using Quotes, Type[A]): Expr[IntersectionTypeMirror[A]] = {
    import quotes.reflect.*

    val tplPrependType = TypeRepr.of[? *: ?] match
      case AppliedType(tycon, _) => tycon
    val tplConcatType = TypeRepr.of[Tuple.Concat]

    def prependTypes(head: TypeRepr, tail: TypeRepr): TypeRepr =
      AppliedType(tplPrependType, List(head, tail))

    def concatTypes(left: TypeRepr, right: TypeRepr): TypeRepr =
      AppliedType(tplConcatType, List(left, right))

    def rec(tpe: TypeRepr): TypeRepr = {
      tpe.dealias match
        case AndType(left, right) => concatTypes(rec(left), rec(right))
        case t                    =>
          // Intentionally using `tpe` instead of `t`. Dealiased representation `t` "loses" information
          // about the original type from the intersection. For example, an Iron predicate `MinLength[N]`
          // would be dealiased to `DescribedAs[Length[GreaterEqual[N]], _]`.
          // Then, a given `ValidatorForPredicate[T, MinLength[N]]` would not be used in implicit resolution.
          prependTypes(tpe, TypeRepr.of[EmptyTuple])
    }
    val tupled =
      TypeRepr.of[A].dealias match {
        case and: AndType => rec(and).asType.asInstanceOf[Type[Elems]]
        case tpe          => report.errorAndAbort(s"${tpe.show} is not an intersection type")
      }
    type Elems

    given Type[Elems] = tupled

    Apply( // Passing the type using quotations causes the type to not be inlined
      TypeApply(
        Select.unique(
          New(
            Applied(
              TypeTree.of[IntersectionTypeMirrorImpl],
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
    ).asExprOf[IntersectionTypeMirror[A]]
  }
}
