package sttp.tapir.server

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir._
import sttp.tapir.capabilities.NoStreams

class EndpointBodyVerifierTest extends AnyFlatSpec with Matchers {
  it should "accept an endpoint with one secondary and one primary body" in {
    val e = endpoint.post.in("people").securityIn(stringBody.asSecondary).in(stringBody)
    EndpointBodyVerifier.verifyOne(e) shouldBe EndpointBodyProblems(Nil, Nil)
  }

  it should "accept an endpoint with a single plain body" in {
    EndpointBodyVerifier.verifyOne(endpoint.post.in("people").in(stringBody)) shouldBe EndpointBodyProblems(Nil, Nil)
  }

  it should "treat a hidden security body as secondary" in {
    val e = endpoint.post.in("people").securityIn(byteArrayBody.schema(_.hidden(true))).in(stringBody)
    EndpointBodyVerifier.verifyOne(e) shouldBe EndpointBodyProblems(Nil, Nil)
  }

  it should "not warn about a lone hidden body" in {
    val e = endpoint.post.in("people").securityIn(byteArrayBody.schema(_.hidden(true)))
    EndpointBodyVerifier.verifyOne(e) shouldBe EndpointBodyProblems(Nil, Nil)
  }

  it should "accept a hidden body alongside a multipart body, as before secondary bodies existed" in {
    val e = endpoint.post.in("people").securityIn(byteArrayBody.schema(_.hidden(true))).in(multipartBody)
    EndpointBodyVerifier.verifyOne(e) shouldBe EndpointBodyProblems(Nil, Nil)
  }

  it should "still count a hidden file body as primary" in {
    val e = endpoint.post.in("people").securityIn(fileBody.schema(_.hidden(true))).in(stringBody)
    EndpointBodyVerifier.verifyOne(e).errors.head should include("declares a request body in both securityIn and in")
  }

  it should "reject a secondary output and error output body" in {
    val e = endpoint.post.in("people").out(stringBody.asSecondary).errorOut(stringBody.asSecondary)
    val problems = EndpointBodyVerifier.verifyOne(e)

    problems.errors should have size 2
    all(problems.errors) should include("Only request bodies can be secondary")
  }

  it should "reject a secondary output body nested in oneOfBody and oneOf variants" in {
    val e = endpoint.post
      .in("people")
      .out(oneOfBody(stringBody.asSecondary))
      .errorOut(sttp.tapir.oneOf[String](oneOfDefaultVariant(stringBody.asSecondary)))
    val problems = EndpointBodyVerifier.verifyOne(e)

    problems.errors should have size 2
    all(problems.errors) should include("Only request bodies can be secondary")
  }

  it should "reject a file body carrying the secondary marker copied from another body" in {
    val marked = fileBody.copy(info = fileBody.info.copy(attributes = stringBody.asSecondary.info.attributes))
    val problems = EndpointBodyVerifier.verifyOne(endpoint.post.in("people").securityIn(marked).in(stringBody))

    problems.errors.head should include("only bodies which can be re-read from buffered bytes")
  }

  it should "reject two primary bodies across securityIn and in" in {
    val e = endpoint.post.in("people").securityIn(stringBody).in(stringBody)
    val problems = EndpointBodyVerifier.verifyOne(e)

    problems.errors should have size 1
    problems.errors.head should include("declares a request body in both securityIn and in")
    problems.errors.head should include("asSecondary")
  }

  it should "reject a streaming primary body combined with a secondary body" in {
    val e = endpoint.post
      .in("people")
      .securityIn(stringBody.asSecondary)
      .in[Nothing, Nothing, Unit, NoStreams](streamTextBody(NoStreams)(CodecFormat.TextPlain()))
    val problems = EndpointBodyVerifier.verifyOne(e)

    problems.errors should have size 1
    problems.errors.head should include("streaming body")
  }

  it should "reject a file body primary combined with a secondary body" in {
    val e = endpoint.post
      .in("people")
      .securityIn(stringBody.asSecondary)
      .in(fileBody)
    val problems = EndpointBodyVerifier.verifyOne(e)

    problems.errors should have size 1
    problems.errors.head should include("file")
  }

  it should "reject a oneOfBody of streaming variants combined with a secondary body" in {
    val e = endpoint.post
      .in("people")
      .securityIn(stringBody.asSecondary)
      .in[Nothing, Unit](oneOfBody[Nothing](streamTextBody(NoStreams)(CodecFormat.TextPlain()).toEndpointIO))
    val problems = EndpointBodyVerifier.verifyOne(e)

    problems.errors should have size 1
    problems.errors.head should include("streaming body")
  }

  it should "reject a oneOfBody with a file body variant combined with a secondary body" in {
    val e = endpoint.post
      .in("people")
      .securityIn(stringBody.asSecondary)
      .in(oneOfBody(fileBody))
    val problems = EndpointBodyVerifier.verifyOne(e)

    problems.errors should have size 1
    problems.errors.head should include("file")
  }

  it should "reject a secondary body variant inside a oneOfBody" in {
    val e = endpoint.post.in("people").securityIn(oneOfBody(stringBody.asSecondary)).in(byteArrayBody)
    val problems = EndpointBodyVerifier.verifyOne(e)

    problems.errors should have size 1
    problems.errors.head should include("marks a oneOfBody variant as secondary")
  }

  it should "warn about a secondary body with no primary body on POST" in {
    val e = endpoint.post.in("ingest").securityIn(stringBody.asSecondary)
    val problems = EndpointBodyVerifier.verifyOne(e)

    problems.errors shouldBe empty
    problems.warnings should have size 1
    problems.warnings.head should include("no request body is part of the API contract")
  }

  it should "not warn about a secondary body with no primary body on GET" in {
    val e = endpoint.get.in("ping").securityIn(stringBody.asSecondary)
    EndpointBodyVerifier.verifyOne(e).warnings shouldBe empty
  }

  it should "warn about metadata on a secondary body" in {
    val e = endpoint.post
      .in("people")
      .securityIn(stringBody.description("the raw payload").asSecondary)
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
