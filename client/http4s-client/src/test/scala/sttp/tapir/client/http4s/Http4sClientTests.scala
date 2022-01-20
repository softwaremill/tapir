package sttp.tapir.client.http4s

import cats.effect.IO
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.{Request, Response, Uri}
import sttp.tapir.client.tests.ClientTests
import sttp.tapir.{DecodeResult, Endpoint}
import scala.concurrent.ExecutionContext.global

abstract class Http4sClientTests[R] extends ClientTests[R] {
  override def send[A, I, E, O](
      e: Endpoint[A, I, E, O, R],
      port: Port,
      securityArgs: A,
      args: I,
      scheme: String = "http"
  ): IO[Either[E, O]] = {
    val (request, parseResponse) =
      Http4sClientInterpreter[IO]()
        .toSecureRequestThrowDecodeFailures(e, Some(Uri.unsafeFromString(s"http://localhost:$port")))
        .apply(securityArgs)
        .apply(args)

    sendAndParseResponse(request, parseResponse)
  }

  override def safeSend[A, I, E, O](e: Endpoint[A, I, E, O, R], port: Port, securityArgs: A, args: I): IO[DecodeResult[Either[E, O]]] = {
    val (request, parseResponse) =
      Http4sClientInterpreter[IO]()
        .toSecureRequest(e, Some(Uri.unsafeFromString(s"http://localhost:$port")))
        .apply(securityArgs)
        .apply(args)

    sendAndParseResponse(request, parseResponse)
  }

  private def sendAndParseResponse[Result](request: Request[IO], parseResponse: Response[IO] => IO[Result]) =
    BlazeClientBuilder[IO](global).resource.use { client =>
      client.run(request).use(parseResponse)
    }
}
