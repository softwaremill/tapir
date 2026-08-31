package sttp.tapir.server

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir._
import sttp.tapir.capabilities.NoStreams

class EndpointBodyVerifierTest extends AnyFlatSpec with Matchers {
  it should "accept an endpoint with one extracted and one primary body" in {
    val e = endpoint.post.in("people").securityIn(extractBodyFromRequest(stringBody)).in(stringBody)
    EndpointBodyVerifier.verifyOne(e) shouldBe EndpointBodyProblems(Nil, Nil)
  }

  it should "accept an endpoint with a single plain body" in {
    EndpointBodyVerifier.verifyOne(endpoint.post.in("people").in(stringBody)) shouldBe EndpointBodyProblems(Nil, Nil)
  }

  it should "reject two primary bodies across securityIn and in" in {
    val e = endpoint.post.in("people").securityIn(stringBody).in(stringBody)
    val problems = EndpointBodyVerifier.verifyOne(e)

    problems.errors should have size 1
    problems.errors.head should include("declares a request body in both securityIn and in")
    problems.errors.head should include("extractBodyFromRequest")
  }

  it should "reject a streaming primary body combined with an extracted body" in {
    val e = endpoint.post
      .in("people")
      .securityIn(extractBodyFromRequest(stringBody))
      .in[Nothing, Nothing, Unit, NoStreams](streamTextBody(NoStreams)(CodecFormat.TextPlain()))
    val problems = EndpointBodyVerifier.verifyOne(e)

    problems.errors should have size 1
    problems.errors.head should include("streaming body")
  }

  it should "reject a file body primary combined with an extracted body" in {
    val e = endpoint.post
      .in("people")
      .securityIn(extractBodyFromRequest(stringBody))
      .in(fileBody)
    val problems = EndpointBodyVerifier.verifyOne(e)

    problems.errors should have size 1
    problems.errors.head should include("file")
  }

  it should "reject a oneOfBody of streaming variants combined with an extracted body" in {
    val e = endpoint.post
      .in("people")
      .securityIn(extractBodyFromRequest(stringBody))
      .in[Nothing, Unit](oneOfBody[Nothing](streamTextBody(NoStreams)(CodecFormat.TextPlain()).toEndpointIO))
    val problems = EndpointBodyVerifier.verifyOne(e)

    problems.errors should have size 1
    problems.errors.head should include("streaming body")
  }

  it should "reject a oneOfBody with a file body variant combined with an extracted body" in {
    val e = endpoint.post
      .in("people")
      .securityIn(extractBodyFromRequest(stringBody))
      .in(oneOfBody(fileBody))
    val problems = EndpointBodyVerifier.verifyOne(e)

    problems.errors should have size 1
    problems.errors.head should include("file")
  }

  it should "warn about an extracted body with no primary body on POST" in {
    val e = endpoint.post.in("ingest").securityIn(extractBodyFromRequest(stringBody))
    val problems = EndpointBodyVerifier.verifyOne(e)

    problems.errors shouldBe empty
    problems.warnings should have size 1
    problems.warnings.head should include("no request body is part of the API contract")
  }

  it should "not warn about an extracted body with no primary body on GET" in {
    val e = endpoint.get.in("ping").securityIn(extractBodyFromRequest(stringBody))
    EndpointBodyVerifier.verifyOne(e).warnings shouldBe empty
  }

  it should "warn about metadata on an extracted body" in {
    val e = endpoint.post
      .in("people")
      .securityIn(extractBodyFromRequest(stringBody.description("the raw payload")))
      .in(stringBody)
    val problems = EndpointBodyVerifier.verifyOne(e)

    problems.warnings should have size 1
    problems.warnings.head should include("never reaches the documentation")
  }

  it should "aggregate problems across endpoints" in {
    val bad = endpoint.post.in("a").securityIn(stringBody).in(stringBody)
    val good = endpoint.post.in("b").in(stringBody)
    EndpointBodyVerifier.verify(List(bad, good)).errors should have size 1
  }
}
