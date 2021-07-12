package sttp.tapir.internal

import sttp.model.StatusCode
import sttp.tapir.EndpointOutput
import sttp.tapir.EndpointOutput.OneOfMapping

import scala.reflect.ClassTag
import scala.reflect.macros.blackbox

object OneOfMappingMacro {
  def generateClassMatcherIfErasedSameAsType[O: c.WeakTypeTag](c: blackbox.Context)(
      statusCode: c.Expr[StatusCode],
      output: c.Expr[EndpointOutput[O]]
  )(ct: c.Expr[ClassTag[O]]): c.Expr[OneOfMapping[O]] = {
    import c.universe._

    val t = implicitly[c.WeakTypeTag[O]].tpe.dealias

    if (!(t =:= t.erasure) && !(t =:= typeOf[Unit])) {
      c.error(
        c.enclosingPosition,
        s"Constructing oneOfMapping of type $t is not allowed because of type erasure. Using a runtime-class-based check it isn't possible to verify " +
          s"that the input matches the desired class. Please use oneOfMappingClassMatcher, oneOfMappingValueMatcher or oneOfMappingFromMatchType instead"
      )
    }

    c.Expr[OneOfMapping[O]](q"_root_.sttp.tapir.oneOfMappingClassMatcher($statusCode, $output, $ct.runtimeClass)")
  }
}
