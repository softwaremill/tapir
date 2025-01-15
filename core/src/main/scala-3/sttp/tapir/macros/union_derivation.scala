package sttp.tapir.macros

import scala.compiletime.*
import scala.deriving.*
import scala.quoted.*

@scala.annotation.implicitNotFound("${A} is not a union of ${T}")
private[tapir] sealed trait IsUnionOf[T, A]

private[tapir] object IsUnionOf:

  private val singleton: IsUnionOf[Any, Any] = new IsUnionOf[Any, Any] {}

  transparent inline given derived[T, A]: IsUnionOf[T, A] = ${ deriveImpl[T, A] }

  private def deriveImpl[T, A](using quotes: Quotes, t: Type[T], a: Type[A]): Expr[IsUnionOf[T, A]] =
    import quotes.reflect.*
    val tpe: TypeRepr = TypeRepr.of[A]
    val bound: TypeRepr = TypeRepr.of[T]

    def validateTypes(tpe: TypeRepr): Unit =
      tpe.dealias match
        case o: OrType =>
          validateTypes(o.left)
          validateTypes(o.right)
        case o =>
          if o <:< bound then ()
          else report.errorAndAbort(s"${o.show} is not a subtype of ${bound.show}")

    tpe.dealias match
      case o: OrType =>
        validateTypes(o)
        ('{ IsUnionOf.singleton.asInstanceOf[IsUnionOf[T, A]] }).asExprOf[IsUnionOf[T, A]]
      case o =>
        if o <:< bound then ('{ IsUnionOf.singleton.asInstanceOf[IsUnionOf[T, A]] }).asExprOf[IsUnionOf[T, A]]
        else report.errorAndAbort(s"${tpe.show} is not a Union")

private[tapir] object UnionDerivation:
  transparent inline def constValueUnionTuple[T, A](using IsUnionOf[T, A]): Tuple = ${ constValueUnionTupleImpl[T, A] }

  private def constValueUnionTupleImpl[T: Type, A: Type](using Quotes): Expr[Tuple] =
    Expr.ofTupleFromSeq(constTypes[T, A])

  private def constTypes[T: Type, A: Type](using Quotes): List[Expr[Any]] =
    import quotes.reflect.*
    val tpe: TypeRepr = TypeRepr.of[A]
    val bound: TypeRepr = TypeRepr.of[T]

    def transformTypes(tpe: TypeRepr): List[TypeRepr] =
      tpe.dealias match
        case o: OrType =>
          transformTypes(o.left) ::: transformTypes(o.right)
        case o: Constant if o <:< bound && o.isSingleton =>
          o :: Nil
        case o =>
          report.errorAndAbort(s"${o.show} is not a subtype of ${bound.show}")

    transformTypes(tpe).distinct.map(_.asType match
      case '[t] => '{ constValue[t] })
