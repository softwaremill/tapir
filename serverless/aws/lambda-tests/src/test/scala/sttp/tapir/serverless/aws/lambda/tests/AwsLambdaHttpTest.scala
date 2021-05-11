package sttp.tapir.serverless.aws.lambda.tests

import cats.effect.IO
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.{basicRequest, _}
import sttp.model.{Header, Uri}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.backendResource

class AwsLambdaHttpTest extends AnyFunSuite {

  private val baseUri: Uri = uri"http://localhost:3000"

//  testServer(empty_endpoint, "GET empty endpoint") { backend =>
//    basicRequest.get(baseUri).send(backend).map(_.body shouldBe Right(""))
//  }
//
//  testServer(empty_endpoint, "POST empty endpoint") { backend =>
//    basicRequest.post(baseUri).send(backend).map(_.body shouldBe Right(""))
//  }
//
//  testServer(empty_get_endpoint, "GET a GET endpoint") { backend =>
//    basicRequest.get(baseUri).send(backend).map(_.body shouldBe Right(""))
//  }
//
//  testServer(empty_get_endpoint, "POST a GET endpoint") { backend =>
//    basicRequest.post(baseUri).send(backend).map(_.body shouldBe Right(""))
//  }
//
//  testServer(in_path_path_out_string_endpoint) { backend =>
//    basicRequest.get(uri"$baseUri/fruit/orange/amount/20").send(backend).map(_.body shouldBe Right("orange 20"))
//  }
//
//  testServer(in_path_path_out_string_endpoint, "with URL encoding") { backend =>
//    basicRequest.get(uri"$baseUri/fruit/apple%2Fred/amount/20").send(backend).map(_.body shouldBe Right("apple/red 20"))
//  }

  testServer(in_string_out_string_endpoint) { backend =>
    basicRequest.post(uri"$baseUri/api/echo/string").body("Sweet").send(backend).map(_.body shouldBe Right("Sweet"))
  }

  testServer(in_json_out_json_endpoint) { backend =>
    basicRequest
      .post(uri"$baseUri/api/echo/json")
      .body("""{"fruit":"orange","amount":11}""")
      .send(backend)
      .map(_.body shouldBe Right("""{"fruit":"orange","amount":11}"""))
  }

  testServer(in_headers_out_headers_endpoint) { backend =>
    basicRequest
      .get(uri"$baseUri/api/echo/headers")
      .headers(Header.unsafeApply("X-Fruit", "apple"), Header.unsafeApply("Y-Fruit", "Orange"))
      .send(backend)
      .map(_.headers should contain allOf (Header.unsafeApply("X-Fruit", "apple"), Header.unsafeApply("Y-Fruit", "Orange")))
  }

  private def testServer(t: ServerEndpoint[_, _, _, Any, IO], suffix: String = "")(
      f: SttpBackend[IO, Fs2Streams[IO] with WebSockets] => IO[Assertion]
  ): Unit = test(s"${t.endpoint.showDetail} $suffix")(backendResource.use(f(_)).unsafeRunSync())
}
