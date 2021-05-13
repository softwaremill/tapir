package sttp.tapir.macros

import sttp.model.StatusCode
import sttp.tapir.EndpointOutput.OneOfMapping
import sttp.tapir.EndpointOutput

import scala.reflect.ClassTag
import scala.reflect.classTag

import scala.quoted.*

trait TapirMacros {

  /** Create a one-of-mapping which uses `statusCode` and `output` if the class of the provided value (when interpreting
    * as a server) matches the runtime class of `T`.
    *
    * This will fail at compile-time if the type erasure of `T` is different from `T`, as a runtime check in this
    * situation would give invalid results. In such cases, use [[oneOfMappingClassMatcher]],
    * [[oneOfMappingValueMatcher]] or [[oneOfMappingFromMatchType]] instead.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  inline def oneOfMapping[T: ClassTag](statusCode: StatusCode, output: EndpointOutput[T]): OneOfMapping[T] = 
    ${ TapirMacros.oneOfMappingImpl[T]('statusCode, 'output, '{classTag[T]}) }

  @scala.deprecated("Use oneOfMapping", since = "0.18")
  inline def statusMapping[T: ClassTag](statusCode: StatusCode, output: EndpointOutput[T]): OneOfMapping[T] = 
    ${ TapirMacros.oneOfMappingImpl[T]('statusCode, 'output, '{classTag[T]}) }

}

object TapirMacros {
  def oneOfMappingImpl[T: Type](statusCode: Expr[StatusCode], output: Expr[EndpointOutput[T]], ct: Expr[ClassTag[T]])(using Quotes): Expr[OneOfMapping[T]] = {
    import quotes.reflect._

    val t = TypeRepr.of[T]

    val isAllowed: TypeRepr => Boolean = {
      case _: AppliedType | _: AndOrType => false
      case _ => true
    }
    
    if (!isAllowed(t)) {
      report.throwError(
        s"Constructing oneOfMapping of type $t is not allowed because of type erasure. Using a runtime-class-based check it isn't possible to verify " +
          s"that the input matches the desired class. Please use oneOfMappingClassMatcher, oneOfMappingValueMatcher or oneOfMappingFromMatchType instead"
      )
    } else {
      '{ sttp.tapir.oneOfMappingClassMatcher($statusCode, $output, $ct.runtimeClass) }
    }
  }
}