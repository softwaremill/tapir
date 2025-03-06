//package sttp.tapir.client.sttp4.ws
//
//import cats.effect.IO
//import org.apache.pekko.actor.ActorSystem
//import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
//import sttp.capabilities.pekko.PekkoStreams
//import sttp.capabilities.{Streams, WebSockets}
//import sttp.client4.WebSocketBackend
//import sttp.client4.pekkohttp.PekkoHttpBackend
//import sttp.tapir.{DecodeResult, Endpoint}
//import sttp.tapir.client.sttp4.WebSocketToPipe
//import sttp.tapir.client.sttp4.ws.SttpClientPekkoTests
//import sttp.tapir.client.sttp4.ws.pekkohttp.*
//import sttp.tapir.client.tests.{ClientTests, ClientWebSocketTests}
//
//import scala.concurrent.Future
//
//class SttpClientWebSocketPekkoTests extends SttpClientPekkoTests[WebSockets with PekkoStreams] with ClientWebSocketTests[PekkoStreams] {
//  override val streams: Streams[PekkoStreams] = PekkoStreams
//  override def wsToPipe: WebSocketToPipe[WebSockets with PekkoStreams] = implicitly
//
//  override def sendAndReceiveLimited[A, B](p: streams.Pipe[A, B], receiveCount: Port, as: List[A]): IO[List[B]] = {
//    val futureResult = Source(as).via(p.asInstanceOf[Flow[A, B, Any]]).take(receiveCount.longValue).runWith(Sink.seq).map(_.toList)
//    IO.fromFuture(IO(futureResult))
//  }
//
//  webSocketTests()
//}
//
//object SttpClientWebSocketPekkoTests {
//  sealed abstract class Base[R <: WebSockets with PekkoStreams] extends ClientTests[R] {
//    implicit val actorSystem: ActorSystem = ActorSystem("tests")
//    val backend: WebSocketBackend[Future] = PekkoHttpBackend.usingActorSystem(actorSystem)
//    def wsToPipe: WebSocketToPipe[R]
//
//    // only web socket tests
//    override def send[A, I, E, O](
//                                   e: Endpoint[A, I, E, O, R],
//                                   port: Port,
//                                   securityArgs: A,
//                                   args: I,
//                                   scheme: String = "http"
//                                 ): IO[Either[E, O]] = {
//      //    implicit val wst: WebSocketToPipe[R] = wsToPipe
//      IO.fromFuture(
//        IO {
//          WebSocketSttpClientInterpreter()
//            .toSecureRequestThrowDecodeFailures(e, Some(uri"$scheme://localhost:$port"))
//            .apply(securityArgs)
//            .apply(args)
//            .send(backend)
//            .map(_.body)
//        }
//      )
//    }
//
//    override def safeSend[A, I, E, O](
//                                       e: Endpoint[A, I, E, O, R],
//                                       port: Port,
//                                       securityArgs: A,
//                                       args: I
//                                     ): IO[DecodeResult[Either[E, O]]] = {
//      implicit val wst: WebSocketToPipe[R] = wsToPipe
//      IO.fromFuture(
//        IO {
//          WebSocketSttpClientInterpreter()
//            .toSecureRequest(e, Some(uri"http://localhost:$port"))
//            .apply(securityArgs)
//            .apply(args)
//            .send(backend)
//            .map(_.body)
//        }
//      )
//    }
//  }
//
//
//}
//
