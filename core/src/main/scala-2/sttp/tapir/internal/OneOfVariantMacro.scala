package sttp.tapir.internal

import sttp.model.StatusCode
import sttp.tapir.EndpointOutput
import sttp.tapir.EndpointOutput.OneOfVariant

import scala.reflect.ClassTag
import scala.reflect.macros.blackbox

object OneOfVariantMacro {
  def generateClassMatcherIfErasedSameAsType[O: c.WeakTypeTag](c: blackbox.Context)(
      output: c.Expr[EndpointOutput[O]]
  )(ct: c.Expr[ClassTag[O]]): c.Expr[OneOfVariant[O]] = {
    import c.universe._
    mustBeEqualToItsErasure[O](c)
    c.Expr[OneOfVariant[O]](q"_root_.sttp.tapir.oneOfVariantClassMatcher($output, $ct.runtimeClass)")
  }

  def generateClassMatcherIfErasedSameAsTypeWithStatusCode[O: c.WeakTypeTag](c: blackbox.Context)(
      code: c.Expr[StatusCode],
      output: c.Expr[EndpointOutput[O]]
  )(ct: c.Expr[ClassTag[O]]): c.Expr[OneOfVariant[O]] = {
    import c.universe._
    mustBeEqualToItsErasure[O](c)
    c.Expr[OneOfVariant[O]](
      q"_root_.sttp.tapir.oneOfVariantClassMatcher(_root_.sttp.tapir.statusCode($code).and($output), $ct.runtimeClass)"
    )
  }

  private def mustBeEqualToItsErasure[O: c.WeakTypeTag](c: blackbox.Context): Unit = {
    import c.universe._

    val t = implicitly[c.WeakTypeTag[O]].tpe.dealias

    if (!(t =:= t.erasure) && !(t =:= typeOf[Unit])) {
      c.error(
        c.enclosingPosition,
        s"Constructing oneOfVariant of type $t is not allowed because of type erasure. Using a runtime-class-based check it isn't possible to verify " +
          s"that the input matches the desired class. Please use oneOfVariantClassMatcher, oneOfVariantValueMatcher or oneOfVariantFromMatchType instead"
      )
    }
  }
}
