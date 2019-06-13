package tapir.client.generated

import tapir.EndpointInput.RequestMethod
import tapir._
import tapir.client.generated.TypeDeclaration.TypeName
import tapir.internal._

class EndpointToGenerated[L: Language](language: L, packageName: String, httpCaller: HttpCaller[L]) {

  def generate[O](apiName: String, endpoint: Endpoint[_, _, O, _]): String = {
    val inputs = endpoint.input.asVectorOfBasicInputs(includeAuth = false)
    val outcome = inputsToOutcomeMap(inputs)
    Language[L].generateCode(apiName, Set.empty, outcome, httpCaller)
  }

  private def inputsToOutcomeMap(inputs: Seq[EndpointInput.Basic[_]]): Set[OutcomeOfPathElement] = {
    val call: Set[OutcomeOfPathElement] = inputs.collect {
      case RequestMethod(method) =>
        Set(HttpCall(method.m, None, TypeName("String"), TypeName("String")))
    }.flatten.toSet

    inputs.foldRight(call) {
      case (EndpointInput.PathSegment(s), acc) =>
        Set(Function(s, acc))
      case (EndpointInput.PathCapture(codec, name, info), acc) =>
        Set(Parameter(name.get, "String", acc))
      case (EndpointInput.PathsCapture(info), acc) =>
        Set(Function(info.description.get, acc))
      case (e, acc) =>
        acc
    }
  }
}
