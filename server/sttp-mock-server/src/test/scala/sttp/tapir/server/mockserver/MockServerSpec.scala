package sttp.tapir.server.mockserver

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.{Identity, Request, StringBody}
import sttp.client3.testing.SttpBackendStub
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.generic.auto._
import sttp.model.Uri.UriContext
import sttp.tapir.server.mockserver.fixture._
import io.circe.literal._
import sttp.model.{HeaderNames, MediaType, Method, StatusCode, Uri}
import io.circe.{Json, parser}
import io.circe.syntax._
import java.util.UUID

class MockServerSpec extends AnyFlatSpec with Matchers {
  behavior of "SttpMockServerClient"

  private val baseUri = uri"http://localhost:1080"

  private def mkMockServerClient(stubBackend: SttpBackendStub[Identity, Any]): SttpMockServerClient[Identity] =
    SttpMockServerClient(baseUri, stubBackend)

  private val plainEndpoint = endpoint
    .in("api" / "v1" / "echo")
    .post
    .in(stringBody)
    .errorOut(stringBody)
    .out(stringBody)

  private val jsonEndpoint = endpoint
    .in("api" / "v1" / "person")
    .put
    .in(jsonBody[CreatePersonCommand])
    .errorOut(jsonBody[ApiError])
    .out(jsonBody[PersonView])

  it should "create plain text expectation correctly" in {
    val sampleIn = "Hello, world!"
    val sampleOut = "Hello to you!"
    val mockServerClient = mkMockServerClient {
      SttpBackendStub.synchronous.whenRequestMatches { req =>
        req.method == Method.PUT &&
        req.uri == uri"$baseUri/mockserver/expectation" &&
        requestBodyMatch(req) {
          json"""{
                    "httpRequest" : {
                      "method": "POST",
                      "path": "/api/v1/echo",
                      "body" : {
                        "string" : $sampleIn,
                        "contentType" : "text/plain",
                        "type" : "STRING"
                      },
                      "headers": {
                        "Content-Length": ["13"],
                        "Content-Type": ["text/plain"],
                        "Accept-Encoding": ["gzip, deflate"]
                      }
                    },
                    "httpResponse" : {
                      "statusCode": 200,
                      "headers": {
                        "Content-Type": ["text/plain; charset=UTF-8"]
                      },
                      "body" : {
                        "string" : $sampleOut,
                        "contentType" : "text/plain; charset=UTF-8",
                        "type" : "STRING"
                      }
                    }
                  }"""
        }
      } thenRespond {
        Right(
          json"""[
                  {
                    "id": "5d122dd6-ce49-4508-b045-a76d9887aec4",
                    "priority": 0,
                    "httpRequest" : {
                      "method": "POST",
                      "path": "/api/v1/echo",
                      "body" : {
                        "type" : "STRING",
                        "string" : $sampleIn,
                        "contentType" : "text/plain"
                      },
                      "headers": {
                        "Content-Length": ["13"],
                        "Content-Type": ["text/plain"],
                        "Accept-Encoding": ["gzip, deflate"]
                      }
                    },
                    "httpResponse" : {
                      "statusCode": 200,
                      "headers": {
                        "Content-Type": ["text/plain; charset=UTF-8"]
                      },
                      "body" : {
                        "string" : $sampleOut,
                        "contentType" : "text/plain; charset=UTF-8",
                        "type" : "STRING"
                      }
                    },
                    "times": {
                      "remainingTimes": 0,
                      "unlimited": true
                    },
                    "timeToLive": {
                      "timeUnit": "DAYS",
                      "timeToLive": 0,
                      "unlimited": true
                    }
                  }
                ]""".noSpaces
        )
      }
    }

    val expectations = mockServerClient
      .whenInputMatches(plainEndpoint)((), sampleIn)
      .thenSuccess(sampleOut)

    expectations should have size 1
    println(expectations.head.httpRequest.path)
    expectations.head shouldEqual Expectation(
      id = "5d122dd6-ce49-4508-b045-a76d9887aec4",
      priority = 0,
      httpRequest = ExpectationRequestDefinition(
        method = Method.POST,
        path = Uri.unsafeParse(
          "/api/v1/echo"
        ), // NOTE: uri interpolator produces different case class instance having the same string representation =(
        body = Some(
          ExpectationBodyDefinition.PlainBodyDefinition(
            string = sampleIn,
            contentType = MediaType.TextPlain
          )
        ),
        headers = Some(
          Map(
            HeaderNames.ContentLength -> List("13"),
            HeaderNames.ContentType -> List("text/plain"),
            HeaderNames.AcceptEncoding -> List("gzip, deflate")
          )
        )
      ),
      httpResponse = ExpectationResponseDefinition(
        statusCode = StatusCode.Ok,
        body = Some(
          ExpectationBodyDefinition.PlainBodyDefinition(
            string = sampleOut,
            contentType = MediaType.TextPlain.charset("UTF-8")
          )
        ),
        headers = Some(
          Map(
            HeaderNames.ContentType -> List("text/plain; charset=UTF-8")
          )
        )
      ),
      times = ExpectationTimes(unlimited = true, remainingTimes = Some(0)),
      timeToLive = ExpectationTimeToLive(unlimited = true, timeToLive = Some(0), timeUnit = Some("DAYS"))
    )
  }

  it should "create plain text error expectation correctly" in {
    val sampleIn = "Hello, world!"
    val sampleOut = "BOOOM!"
    val mockServerClient = mkMockServerClient {
      SttpBackendStub.synchronous.whenRequestMatches { req =>
        req.method == Method.PUT &&
        req.uri == uri"$baseUri/mockserver/expectation" &&
        requestBodyMatch(req) {
          json"""{
                    "httpRequest" : {
                      "method": "POST",
                      "path": "/api/v1/echo",
                      "body" : {
                        "string" : $sampleIn,
                        "contentType" : "text/plain",
                        "type" : "STRING"
                      },
                      "headers": {
                        "Content-Length": ["13"],
                        "Content-Type": ["text/plain"],
                        "Accept-Encoding": ["gzip, deflate"]
                      }
                    },
                    "httpResponse" : {
                      "statusCode": 500,
                      "headers": {
                        "Content-Type": ["text/plain; charset=UTF-8"]
                      },
                      "body" : {
                        "type" : "STRING",
                        "string" : $sampleOut,
                        "contentType" : "text/plain; charset=UTF-8"
                      }
                    }
                  }"""
        }
      } thenRespond {
        Right(
          json"""[
                  {
                    "id": "5d122dd6-ce49-4508-b045-a76d9887aec4",
                    "priority": 0,
                    "httpRequest" : {
                      "method": "POST",
                      "path": "/api/v1/echo",
                      "body" : {
                        "type" : "STRING",
                        "string" : $sampleIn,
                        "contentType" : "text/plain"
                      },
                      "headers": {
                        "Content-Length": ["13"],
                        "Content-Type": ["text/plain"],
                        "Accept-Encoding": ["gzip, deflate"]
                      }
                    },
                    "httpResponse" : {
                      "statusCode": 500,
                      "headers": {
                        "Content-Type": ["text/plain; charset=UTF-8"]
                      },
                      "body" : {
                        "type" : "STRING",
                        "string" : $sampleOut,
                        "contentType" : "text/plain; charset=UTF-8"
                      }
                    },
                    "times": {
                      "remainingTimes": 0,
                      "unlimited": true
                    },
                    "timeToLive": {
                      "timeUnit": "DAYS",
                      "timeToLive": 0,
                      "unlimited": true
                    }
                  }
                ]""".noSpaces
        )
      }
    }

    val expectations = mockServerClient
      .whenInputMatches(plainEndpoint)((), sampleIn)
      .thenError(sampleOut, statusCode = StatusCode.InternalServerError)

    expectations should have size 1
    println(expectations.head.httpRequest.path)
    expectations.head shouldEqual Expectation(
      id = "5d122dd6-ce49-4508-b045-a76d9887aec4",
      priority = 0,
      httpRequest = ExpectationRequestDefinition(
        method = Method.POST,
        path = Uri.unsafeParse(
          "/api/v1/echo"
        ), // NOTE: uri interpolator produces different case class instance having the same string representation =(
        body = Some(
          ExpectationBodyDefinition.PlainBodyDefinition(
            string = sampleIn,
            contentType = MediaType.TextPlain
          )
        ),
        headers = Some(
          Map(
            HeaderNames.ContentLength -> List("13"),
            HeaderNames.ContentType -> List("text/plain"),
            HeaderNames.AcceptEncoding -> List("gzip, deflate")
          )
        )
      ),
      httpResponse = ExpectationResponseDefinition(
        statusCode = StatusCode.InternalServerError,
        body = Some(
          ExpectationBodyDefinition.PlainBodyDefinition(
            string = sampleOut,
            contentType = MediaType.TextPlain.charset("UTF-8")
          )
        ),
        headers = Some(
          Map(
            HeaderNames.ContentType -> List("text/plain; charset=UTF-8")
          )
        )
      ),
      times = ExpectationTimes(unlimited = true, remainingTimes = Some(0)),
      timeToLive = ExpectationTimeToLive(unlimited = true, timeToLive = Some(0), timeUnit = Some("DAYS"))
    )
  }

  it should "create json expectation correctly" in {
    val sampleIn = CreatePersonCommand(name = "John", age = 23)
    val sampleOut = PersonView(id = UUID.fromString("43ab1680-7aa0-4cf7-99cc-10abfe76b424"), name = "John", age = 23)

    val mockServerClient = mkMockServerClient {
      SttpBackendStub.synchronous.whenRequestMatches { req =>
        req.method == Method.PUT &&
        req.uri == uri"$baseUri/mockserver/expectation" &&
        requestBodyMatch(req) {
          json"""{
                    "httpRequest" : {
                      "method": "PUT",
                      "path": "/api/v1/person",
                      "body" : {
                        "json" : $sampleIn,
                        "matchType": "STRICT",
                        "type" : "JSON"
                      },
                      "headers": {
                        "Content-Length": ["24"],
                        "Content-Type": ["application/json"],
                        "Accept-Encoding": ["gzip, deflate"]
                      }
                    },
                    "httpResponse" : {
                      "statusCode": 200,
                      "headers": {
                        "Content-Type": ["application/json"]
                      },
                      "body" : {
                        "json" : $sampleOut,
                        "matchType": "STRICT",
                        "type" : "JSON"
                      }
                    }
                  }"""
        }
      } thenRespond {
        Right(
          json"""[
                  {
                    "id": "5d122dd6-ce49-4508-b045-a76d9887aec4",
                    "priority": 0,
                    "httpRequest" : {
                      "method": "PUT",
                      "path": "/api/v1/person",
                      "body" : {
                        "json" : $sampleIn,
                        "matchType": "STRICT",
                        "type" : "JSON"
                      },
                      "headers": {
                        "Content-Length": ["24"],
                        "Content-Type": ["application/json"],
                        "Accept-Encoding": ["gzip, deflate"]
                      }
                    },
                    "httpResponse" : {
                      "statusCode": 200,
                      "headers": {
                        "Content-Type": ["application/json"]
                      },
                      "body" : $sampleOut
                    },
                    "times": {
                      "remainingTimes": 0,
                      "unlimited": true
                    },
                    "timeToLive": {
                      "timeUnit": "DAYS",
                      "timeToLive": 0,
                      "unlimited": true
                    }
                  }
                ]""".noSpaces
        )
      }
    }

    val expectations = mockServerClient
      .whenInputMatches(jsonEndpoint)((), sampleIn)
      .thenSuccess(sampleOut)

    expectations should have size 1
    println(expectations.head.httpRequest.path)
    expectations.head shouldEqual Expectation(
      id = "5d122dd6-ce49-4508-b045-a76d9887aec4",
      priority = 0,
      httpRequest = ExpectationRequestDefinition(
        method = Method.PUT,
        path = Uri.unsafeParse(
          "/api/v1/person"
        ), // NOTE: uri interpolator produces different case class instance having the same string representation =(
        body = Some(
          ExpectationBodyDefinition.JsonBodyDefinition(
            json = sampleIn.asJsonObject,
            matchType = ExpectationBodyDefinition.JsonMatchType.Strict
          )
        ),
        headers = Some(
          Map(
            HeaderNames.ContentLength -> List("24"),
            HeaderNames.ContentType -> List("application/json"),
            HeaderNames.AcceptEncoding -> List("gzip, deflate")
          )
        )
      ),
      httpResponse = ExpectationResponseDefinition(
        statusCode = StatusCode.Ok,
        body = Some(ExpectationBodyDefinition.RawJson(sampleOut.asJsonObject)),
        headers = Some(
          Map(
            HeaderNames.ContentType -> List("application/json")
          )
        )
      ),
      times = ExpectationTimes(unlimited = true, remainingTimes = Some(0)),
      timeToLive = ExpectationTimeToLive(unlimited = true, timeToLive = Some(0), timeUnit = Some("DAYS"))
    )
  }

  it should "create error json expectation correctly" in {
    val sampleIn = CreatePersonCommand(name = "John", age = -1)
    val sampleErrorOut = ApiError(code = 1, message = "Invalid age")

    val mockServerClient = mkMockServerClient {
      SttpBackendStub.synchronous.whenRequestMatches { req =>
        req.method == Method.PUT &&
        req.uri == uri"$baseUri/mockserver/expectation" &&
        requestBodyMatch(req) {
          json"""{
                    "httpRequest" : {
                      "method": "PUT",
                      "path": "/api/v1/person",
                      "body" : {
                        "json" : $sampleIn,
                        "matchType": "STRICT",
                        "type" : "JSON"
                      },
                      "headers": {
                        "Content-Length": ["24"],
                        "Content-Type": ["application/json"],
                        "Accept-Encoding": ["gzip, deflate"]
                      }
                    },
                    "httpResponse" : {
                      "statusCode": 400,
                      "headers": {
                        "Content-Type": ["application/json"]
                      },
                      "body" : {
                        "json" : $sampleErrorOut,
                        "matchType": "STRICT",
                        "type" : "JSON"
                      }
                    }
                  }"""
        }
      } thenRespond {
        Right(
          json"""[
                  {
                    "id": "5d122dd6-ce49-4508-b045-a76d9887aec4",
                    "priority": 0,
                    "httpRequest" : {
                      "method": "PUT",
                      "path": "/api/v1/person",
                      "body" : {
                        "json" : $sampleIn,
                        "matchType": "STRICT",
                        "type" : "JSON"
                      },
                      "headers": {
                        "Content-Length": ["24"],
                        "Content-Type": ["application/json"],
                        "Accept-Encoding": ["gzip, deflate"]
                      }
                    },
                    "httpResponse" : {
                      "statusCode": 400,
                      "headers": {
                        "Content-Type": ["application/json"]
                      },
                      "body" : $sampleErrorOut
                    },
                    "times": {
                      "remainingTimes": 0,
                      "unlimited": true
                    },
                    "timeToLive": {
                      "timeUnit": "DAYS",
                      "timeToLive": 0,
                      "unlimited": true
                    }
                  }
                ]""".noSpaces
        )
      }
    }

    val expectations = mockServerClient
      .whenInputMatches(jsonEndpoint)((), sampleIn)
      .thenError(sampleErrorOut, statusCode = StatusCode.BadRequest)

    expectations should have size 1
    println(expectations.head.httpRequest.path)
    expectations.head shouldEqual Expectation(
      id = "5d122dd6-ce49-4508-b045-a76d9887aec4",
      priority = 0,
      httpRequest = ExpectationRequestDefinition(
        method = Method.PUT,
        path = Uri.unsafeParse(
          "/api/v1/person"
        ), // NOTE: uri interpolator produces different case class instance having the same string representation =(
        body = Some(
          ExpectationBodyDefinition.JsonBodyDefinition(
            json = sampleIn.asJsonObject,
            matchType = ExpectationBodyDefinition.JsonMatchType.Strict
          )
        ),
        headers = Some(
          Map(
            HeaderNames.ContentLength -> List("24"),
            HeaderNames.ContentType -> List("application/json"),
            HeaderNames.AcceptEncoding -> List("gzip, deflate")
          )
        )
      ),
      httpResponse = ExpectationResponseDefinition(
        statusCode = StatusCode.BadRequest,
        body = Some(ExpectationBodyDefinition.RawJson(sampleErrorOut.asJsonObject)),
        headers = Some(
          Map(
            HeaderNames.ContentType -> List("application/json")
          )
        )
      ),
      times = ExpectationTimes(unlimited = true, remainingTimes = Some(0)),
      timeToLive = ExpectationTimeToLive(unlimited = true, timeToLive = Some(0), timeUnit = Some("DAYS"))
    )
  }

  private def requestBodyMatch(req: Request[_, _])(that: Json): Boolean = {
    req.body match {
      case string: StringBody =>
        parser.parse(string.s) == Right(that)
      case _ => false
    }
  }
}
