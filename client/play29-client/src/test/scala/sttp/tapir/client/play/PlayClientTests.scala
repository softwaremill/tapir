package sttp.tapir.client.play

import akka.actor.ActorSystem
import akka.stream.Materializer
import cats.effect.IO
import play.api.libs.ws.StandaloneWSClient
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import sttp.tapir.client.tests.ClientTests
import sttp.tapir.{DecodeResult, Endpoint}

import scala.concurrent.Future

abstract class PlayClientTests[R] extends ClientTests[R] {

  implicit val materializer: Materializer = Materializer(ActorSystem("tests"))

  implicit val wsClient: StandaloneWSClient = StandaloneAhcWSClient()

  override def send[A, I, E, O](
      e: Endpoint[A, I, E, O, R],
      port: Port,
      securityArgs: A,
      args: I,
      scheme: String = "http"
  ): IO[Either[E, O]] = {
    def response: Future[Either[E, O]] = {
      val (req, responseParser) =
        PlayClientInterpreter().toSecureRequestThrowDecodeFailures(e, s"http://localhost:$port").apply(securityArgs).apply(args)
      req.execute().map(responseParser)
    }
    IO.fromFuture(IO(response))
  }

  override def safeSend[A, I, E, O](
      e: Endpoint[A, I, E, O, R],
      port: Port,
      securityArgs: A,
      args: I
  ): IO[DecodeResult[Either[E, O]]] = {
    def response: Future[DecodeResult[Either[E, O]]] = {
      val (req, responseParser) = PlayClientInterpreter().toSecureRequest(e, s"http://localhost:$port").apply(securityArgs).apply(args)
      req.execute().map(responseParser)
    }
    IO.fromFuture(IO(response))
  }

}
