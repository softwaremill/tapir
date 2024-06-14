package sttp.tapir.server.play

import akka.actor.ActorSystem
import akka.stream.scaladsl.Flow
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.matchers.should.Matchers._
import play.api.Mode
import play.api.routing.Router
import play.core.server.{DefaultAkkaHttpServerComponents, ServerConfig}
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.tapir._
import sttp.tapir.tests.Test
import sttp.ws.{WebSocket, WebSocketFrame}

import scala.concurrent.Future

class PlayServerWithContextTest(backend: SttpBackend[IO, Fs2Streams[IO] with WebSockets])(implicit _actorSystem: ActorSystem) {
  import _actorSystem.dispatcher

  def tests(): List[Test] = List(
    Test("server with play.http.context set") {
      val e = endpoint.get.in("hello").out(stringBody).serverLogicSuccess(_ => Future.successful("world"))
      val components = new DefaultAkkaHttpServerComponents {
        override lazy val serverConfig: ServerConfig = ServerConfig(port = Some(0), address = "127.0.0.1", mode = Mode.Test)
        override lazy val actorSystem: ActorSystem = ActorSystem("tapir", defaultExecutionContext = Some(_actorSystem.dispatcher))
        override lazy val router: Router = Router.from(PlayServerInterpreter().toRoutes(e)).withPrefix("/test")
      }
      val s = components.server
      val r = Future.successful(()).flatMap { _ =>
        basicRequest
          .get(uri"http://localhost:${s.mainAddress.getPort}/test/hello")
          .send(backend)
          .map(_.body shouldBe Right("world"))
          .unsafeToFuture()
      }
      r.onComplete(_ => s.stop())
      r
    },
    Test("websocket with prefixed play route") {
      val e = endpoint.get
        .in("hello")
        .out(webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain](AkkaStreams))
        .serverLogicSuccess[Future](_ => Future.successful(Flow[String].map(_ => "world")))

      val components = new DefaultAkkaHttpServerComponents {
        override lazy val serverConfig: ServerConfig = ServerConfig(port = Some(0), address = "127.0.0.1", mode = Mode.Test)
        override lazy val actorSystem: ActorSystem = ActorSystem("tapir", defaultExecutionContext = Some(_actorSystem.dispatcher))
        override lazy val router: Router = Router.from(PlayServerInterpreter().toRoutes(e)).withPrefix("/test")
      }
      val s = components.server
      val r = basicRequest
        .response(asWebSocket { (ws: WebSocket[IO]) =>
          for {
            _ <- ws.sendText("test1")
            m1 <- ws.receiveText()
            _ <- ws.close()
            m3 <- ws.eitherClose(ws.receiveText())
          } yield List(m1, m3)
        })
        .get(uri"ws://localhost:${s.mainAddress.getPort}/test/hello")
        .send(backend)
        .map(_.body shouldBe Right(List("world", Left(WebSocketFrame.Close(1000, "normal closure")))))
        .unsafeToFuture()
      r.onComplete(_ => s.stop())
      r
    }
  )
}
