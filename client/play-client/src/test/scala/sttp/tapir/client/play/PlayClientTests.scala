package sttp.tapir.client.play

import akka.actor.ActorSystem
import akka.stream.Materializer
import cats.effect.{ContextShift, IO}
import play.api.libs.ws.StandaloneWSClient
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import sttp.tapir.client.tests.ClientTests
import sttp.tapir.{DecodeResult, Endpoint}

import scala.concurrent.{ExecutionContext, Future}

abstract class PlayClientTests[R] extends ClientTests[R] {

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)
  implicit val materializer: Materializer = Materializer(ActorSystem("tests"))

  implicit val wsClient: StandaloneWSClient = StandaloneAhcWSClient()

  override def send[I, E, O](e: Endpoint[I, E, O, R], port: Port, args: I, scheme: String = "http"): IO[Either[E, O]] = {
    def response: Future[Either[E, O]] = {
      val (req, responseParser) = PlayClientInterpreter().toRequestUnsafe(e, s"http://localhost:$port").apply(args)
      req.execute().map(responseParser)
    }
    IO.fromFuture(IO(response))
  }

  override def safeSend[I, E, O](
      e: Endpoint[I, E, O, R],
      port: Port,
      args: I
  ): IO[DecodeResult[Either[E, O]]] = {
    def response: Future[DecodeResult[Either[E, O]]] = {
      val (req, responseParser) = PlayClientInterpreter().toRequest(e, s"http://localhost:$port").apply(args)
      req.execute().map(responseParser)
    }
    IO.fromFuture(IO(response))
  }

}
