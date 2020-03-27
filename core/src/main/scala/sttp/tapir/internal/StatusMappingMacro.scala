package sttp.tapir.internal

import sttp.model.StatusCode
import sttp.tapir.EndpointOutput
import sttp.tapir.EndpointOutput.StatusMapping

import scala.reflect.ClassTag
import scala.reflect.macros.blackbox

object StatusMappingMacro {
  def classMatcherIfErasedSameAsType[O: c.WeakTypeTag](c: blackbox.Context)(
      statusCode: c.Expr[StatusCode],
      output: c.Expr[EndpointOutput[O]]
  )(ct: c.Expr[ClassTag[O]]): c.Expr[StatusMapping[O]] = {
    import c.universe._

    val t = implicitly[c.WeakTypeTag[O]].tpe.dealias

    if (!(t =:= t.erasure)) {
      c.error(
        c.enclosingPosition,
        s"Constructing statusMapping of type $t is not allowed because of type erasure. Using a runtime-class-based check it isn't possible to verify " +
          s"that the input matches the desired class. Please use statusMappingClassMatcher, statusMappingValueMatcher or statusMappingFromMatchType instead"
      )
    }

    c.Expr[StatusMapping[O]](q"sttp.tapir.statusMappingClassMatcher($statusCode, $output, $ct.runtimeClass)")
  }
}
