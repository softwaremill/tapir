package tapir.client
import tapir.client.generated.TypeDeclaration.TypeName

package object generated {
  sealed trait OutcomeOfPathElement
  case class Parameter(name: String, paramTypeName: String, impl: Set[OutcomeOfPathElement]) extends OutcomeOfPathElement
  case class Function(name: String, impl: Set[OutcomeOfPathElement]) extends OutcomeOfPathElement
  case class HttpCall[Payload, Error, Response](
      method: String,
      payloadType: Option[TypeName[Payload]],
      error: TypeName[Error],
      response: TypeName[Response]
  ) extends OutcomeOfPathElement
}
