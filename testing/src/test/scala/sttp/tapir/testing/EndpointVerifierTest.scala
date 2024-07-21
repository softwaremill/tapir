package sttp.tapir.testing

import io.circe.generic.auto._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.model.{Method, StatusCode}
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

class EndpointVerifierTest extends AnyFlatSpecLike with Matchers {

  it should "should not detect shadowed endpoint when first endpoint has empty path and second fixed path" in {
    val e1 = endpoint.get
    val e2 = endpoint.get.in("x")

    val result = EndpointVerifier(List(e1, e2))

    result shouldBe Set()
  }

  it should "not detect shadowed endpoint when second endpoint doesn't have specified method" in {
    val e1 = endpoint.get.in("x")
    val e2 = endpoint.in("x")

    val result = EndpointVerifier(List(e1, e2))

    result shouldBe Set()
  }

  it should "detect shadowed endpoint when first endpoint doesn't have specified method" in {
    val e1 = endpoint.in("x")
    val e2 = endpoint.get.in("x")

    val result = EndpointVerifier(List(e1, e2))

    val expectedResult = Set(ShadowedEndpointError(e2, e1))
    result shouldBe expectedResult
  }

  it should "should detect shadowed endpoint when first endpoint has only wildcard path and second empty path" in {
    val e1 = endpoint.get.in(paths)
    val e2 = endpoint.get
    val result = EndpointVerifier(List(e1, e2))

    val expectedResult = Set(ShadowedEndpointError(e2, e1))
    result shouldBe expectedResult
  }

  it should "detect shadowed endpoint when http methods are provided in different order" in {
    val e1 = endpoint.in("x").get
    val e2 = endpoint.get.in("x")

    val result = EndpointVerifier(List(e1, e2))

    val expectedResult = Set(ShadowedEndpointError(e2, e1))
    result shouldBe expectedResult
  }

  it should "detect shadowed endpoint when security inputs and inputs overlap" in {
    val e1 = endpoint.get.in("x")
    val e2 = endpoint.get.securityIn("x")

    val result = EndpointVerifier(List(e1, e2))

    val expectedResult = Set(ShadowedEndpointError(e2, e1))
    result shouldBe expectedResult
  }

  it should "should not detect shadowed endpoint when methods are not provided and paths are different" in {
    val e1 = endpoint.in("y")
    val e2 = endpoint.in("x")

    val result = EndpointVerifier(List(e1, e2))

    result shouldBe Set()
  }

  it should "should detect shadowed endpoint when methods are not provided" in {
    val e1 = endpoint.in("x")
    val e2 = endpoint.in("x")

    val result = EndpointVerifier(List(e1, e2))

    val expectedResult = Set(ShadowedEndpointError(e2, e1))
    result shouldBe expectedResult
  }

  it should "omit all segments which are not relevant for shadow check" in {
    val e1 = endpoint.get.in(query[String]("key").and(header[String]("X-Account"))).in("x")
    val e2 = endpoint.get.in("x")

    val result = EndpointVerifier(List(e1, e2))

    val expectedResult = Set(ShadowedEndpointError(e2, e1))
    result shouldBe expectedResult
  }

  it should "should detect shadowed endpoints with path variables that contain special characters" in {
    val e1 = endpoint.get.in("[x]")
    val e2 = endpoint.get.in("x")
    val e3 = endpoint.get.in("/..." / paths)
    val e4 = endpoint.get.in("x/")
    val e5 = endpoint.get.in("x")

    val result = EndpointVerifier(List(e1, e2, e3, e4, e5))

    val expectedResult = Set(ShadowedEndpointError(e5, e2))
    result shouldBe expectedResult
  }

  it should "not detect shadowed endpoint when it is only partially shadowed" in {
    val e1 = endpoint.get.in("x/y/z")
    val e2 = endpoint.get.in("x" / "y" / "z")
    val e3 = endpoint.get.in("x" / paths)

    val result = EndpointVerifier(List(e1, e2, e3))

    result shouldBe Set()
  }

  it should "detect endpoint with path variable shadowed by endpoint with wildcard" in {
    val e1 = endpoint.get.in("x" / paths)
    val e2 = endpoint.get.in("x" / path[String].name("y_2") / "z")
    val e3 = endpoint.get.in("x" / "y" / "x")

    val result = EndpointVerifier(List(e1, e2, e3))

    val expectedResult = Set(ShadowedEndpointError(e2, e1), ShadowedEndpointError(e3, e1))
    result shouldBe expectedResult
  }

  it should "detect shadowed endpoints for endpoints with similar path structure but with different path vars names" in {

    val e1 = endpoint.get.in("x" / "y")
    val e2 = endpoint.get.in("x" / "y" / paths)
    val e3 = endpoint.get.in("x" / path[String].name("y_2") / path[String].name("y_4") / "z1")
    val e4 = endpoint.get.in("x" / path[String].name("y_3") / path[String].name("y_5") / "z1")

    val result = EndpointVerifier(List(e1, e2, e3, e4))

    val expectedResult = Set(ShadowedEndpointError(e4, e3))
    result shouldBe expectedResult
  }

  it should "detect shadowed endpoints for endpoints with path variables and wildcards at the beginning" in {
    val e1 = endpoint.get.in("z" / paths)
    val e2 = endpoint.get.in("z" / "x" / path[String].name("y_2") / path[String].name("y4"))
    val e3 = endpoint.get.in("z" / "x" / path[String].name("y_3") / path[String].name("y5"))
    val e4 = endpoint.get.in("c" / "x" / path[String].name("y_3") / path[String].name("y5"))

    val result = EndpointVerifier(List(e1, e2, e3, e4))

    val expectedResult = Set(ShadowedEndpointError(e2, e1), ShadowedEndpointError(e3, e1))
    result shouldBe expectedResult
  }

  it should "detect shadowed endpoints for endpoints with path variables in the middle" in {
    val e1 = endpoint.get.in("x" / path[String].name("y_1") / "z")
    val e2 = endpoint.get.in("x" / path[String].name("y_2") / path[String].name("y4"))
    val e3 = endpoint.get.in("x" / path[String].name("y_3") / path[String].name("y5"))

    val result = EndpointVerifier(List(e1, e2, e3))

    val expectedResult = Set(ShadowedEndpointError(e3, e2))
    result shouldBe expectedResult
  }

  it should "detect shadowed endpoints for fixed paths" in {
    val e1 = endpoint.get.in("y").out(stringBody)
    val e2 = endpoint.get.in("x").out(stringBody)
    val e3 = endpoint.get.in("x").out(stringBody)
    val e4 = endpoint.get.in("x").out(stringBody)
    val e5 = endpoint.get.in("x" / "y").out(stringBody)
    val e6 = endpoint.post.in("x").out(stringBody)

    val result = EndpointVerifier(List(e1, e2, e3, e4, e5, e6))

    val expectedResult = Set(ShadowedEndpointError(e3, e2), ShadowedEndpointError(e4, e2))
    result shouldBe expectedResult
  }

  it should "return empty result when no shadowed endpoints exist" in {
    val e1 = endpoint.get.in("a").out(stringBody)
    val e2 = endpoint.get.in("b").out(stringBody)
    val e3 = endpoint.get.in("a" / "b" / paths).out(stringBody)
    val e4 = endpoint.get.in("a/b/c").out(stringBody)

    val result = EndpointVerifier(List(e1, e2, e3, e4))
    result shouldBe Set()
  }

  it should "return empty result for empty input" in {
    val result = EndpointVerifier(Nil)
    result shouldBe Set()
  }

  it should "not detect incorrect path input when input is fixed" in {
    val e = endpoint.get.in("x" / "y")

    val result = EndpointVerifier(List(e))

    result shouldBe Set()
  }

  it should "not detect incorrect path input when wildcard `paths` segment appears as the last at input" in {
    val e = endpoint.get.in("x" / paths)

    val result = EndpointVerifier(List(e))

    result shouldBe Set()
  }

  it should "not detect incorrect path input when wildcard `paths` segment appears as the last at security input" in {
    val e = endpoint.get.securityIn("x" / paths)

    val result = EndpointVerifier(List(e))

    result shouldBe Set()
  }

  it should "detect incorrect path input when wildcard `paths` segment appears before the last at input" in {
    val e = endpoint.get.in("x" / paths / "y" / "z")

    val result = EndpointVerifier(List(e))

    val expectedResult = Set(IncorrectPathsError(e, 1))
    result shouldBe expectedResult
  }

  it should "detect incorrect path input when wildcard `paths` segment appears before the last security input" in {
    val e = endpoint.get.securityIn("x" / paths / "y" / "z")

    val result = EndpointVerifier(List(e))

    val expectedResult = Set(IncorrectPathsError(e, 1))
    result shouldBe expectedResult
  }

  it should "detect incorrect path input when wildcard `paths` segment and any path is defined in standard input" in {
    val e = endpoint.get.securityIn(paths).in("x")

    val result = EndpointVerifier(List(e))

    val expectedResult = Set(IncorrectPathsError(e, 0))
    result shouldBe expectedResult
  }

  it should "not detect any incorrect path input when wildcard `paths` segment is defined as last segment of path" in {
    val e = endpoint.get.in(paths).securityIn("x")

    val result = EndpointVerifier(List(e))

    result shouldBe Set()
  }

  it should "detect incorrect path input when wildacard `paths` segment overlaps other path segments and return proper index in path" in {
    val e = endpoint.options.in("a" / "b" / "c").securityIn("x" / "y" / paths)

    val result = EndpointVerifier(List(e))

    val expectedResult = Set(IncorrectPathsError(e, 2))
    result shouldBe expectedResult
  }

  it should "detect shadowed endpoints and incorrect path simultaneously" in {
    val e1 = endpoint.options.securityIn("x" / paths).out(stringBody)
    val e2 = endpoint.options.in("a" / "b" / "c").securityIn("x" / "y" / paths).out(stringBody)

    val result = EndpointVerifier(List(e1, e2))

    val expectedResult = Set(ShadowedEndpointError(e2, e1), IncorrectPathsError(e2, 2))
    result shouldBe expectedResult
  }

  it should "detect duplicated methods under endpoint" in {
    val e = endpoint.get.options

    val result = EndpointVerifier(List(e))

    result shouldBe Set(DuplicatedMethodDefinitionError(e, List(Method.GET, Method.OPTIONS)))
  }

  it should "not detect duplicated methods when there is at most one method defined" in {
    val e1 = endpoint.in("a")
    val e2 = endpoint.get.in("b")

    val result = EndpointVerifier(List(e1, e2))

    result shouldBe Set()
  }

  it should "detect endpoints with body where status code doesn't allow a body" in {

    val e1 = endpoint.in("endpoint1_Err").out(stringBody).out(statusCode(StatusCode.NoContent))
    val e2 = endpoint.in("endpoint2_Ok").out(stringBody).out(statusCode(StatusCode.BadRequest))
    val e3 = endpoint.in("endpoint3_Err").out(stringBody).out(statusCode(StatusCode.NotModified))
    val e4 = endpoint.in("endpoint4_ok").out(emptyOutputAs(NoContent)).out(statusCode(StatusCode.NoContent))
    val e5 = endpoint
      .in("endpoint5_err")
      .out(stringBody)
      .errorOut(
        sttp.tapir.oneOf[ErrorInfo](
          oneOfVariant(statusCode(StatusCode.NotFound).and(jsonBody[NotFound])),
          oneOfVariant(statusCode(StatusCode.NoContent).and(jsonBody[NoContentData]))
        )
      )
    val e6 = endpoint
      .in("endpoint6_ok")
      .errorOut(
        sttp.tapir.oneOf[ErrorInfo](
          oneOfVariant(statusCode(StatusCode.NotFound).and(jsonBody[NotFound])),
          oneOfVariant(statusCode(StatusCode.NoContent).and(emptyOutputAs(NoContent)))
        )
      )

    val result = EndpointVerifier(List(e1, e2, e3, e4, e5, e6))

    result shouldBe Set(
      UnexpectedBodyError(e1, StatusCode.NoContent),
      UnexpectedBodyError(e3, StatusCode.NotModified),
      UnexpectedBodyError(e5, StatusCode.NoContent)
    )
  }

  it should "detect duplicate names among endpoints, all endpoints names (operationId's) must be unique" in {
    val e1 = endpoint.in("a").name("Z")
    val e2 = endpoint.in("b").name("Z")

    val result = EndpointVerifier(List(e1, e2))

    result shouldBe Set(DuplicatedNameError("Z"))
  }
}

sealed trait ErrorInfo
case class NotFound(what: String) extends ErrorInfo
case object NoContent extends ErrorInfo
case class NoContentData(msg: String) extends ErrorInfo
