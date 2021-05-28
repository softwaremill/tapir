package sttp.tapir.docs.openapi

import sttp.tapir.openapi.{Info, ResponsesCodeKey}
import sttp.tapir.tests._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.model.StatusCode

class EndpointToOpenAPIDocsTest extends AnyFunSuite with Matchers {
  for (e <- allTestEndpoints) {
    test(s"${e.showDetail} should convert to open api") {
      OpenAPIDocsInterpreter.toOpenAPI(e, Info("title", "19.2-beta-RC1"))
    }
  }

  test("additional success with empty body should be present in openapi specification") {
    import sttp.tapir.{oneOf => one, _}
    sealed trait Base
    case object Success extends Base
    case object AnotherSuccess extends Base

    val endpoint = infallibleEndpoint.get.out(
      one[Base](
        oneOfMapping(StatusCode.Ok, emptyOutputAs(Success)),
        oneOfMapping(StatusCode.Accepted, emptyOutputAs(AnotherSuccess))
      )
    )

    val openapi = OpenAPIDocsInterpreter.toOpenAPI(endpoint, Info("title", "19.2-beta-RC1"))
    import org.scalatest.LoneElement._
    val operation = openapi.paths.pathItems.values.flatMap(_.get).toList.loneElement
    operation.responses.responses.keys should contain only (
      ResponsesCodeKey(StatusCode.Ok.code),
      ResponsesCodeKey(StatusCode.Accepted.code)
    )
  }
}
