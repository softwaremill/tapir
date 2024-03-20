package sttp.tapir.examples

import cats.effect.{IO, IOApp}
import sttp.client3._
import sttp.model.StatusCode
import sttp.tapir.server.netty.cats.NettyCatsServer
import sttp.tapir.*
import scala.concurrent.duration._
import sttp.capabilities.fs2.Fs2Streams
import sttp.ws.WebSocket
import sttp.client3.pekkohttp.PekkoHttpBackend
import scala.concurrent.Future

object WebSocketsNettyCatsServer extends IOApp.Simple {
  // One endpoint on GET /hello with query parameter `name`
  val helloWorldEndpoint: PublicEndpoint[String, Unit, String, Any] =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody)

  val wsEndpoint =
    endpoint.get.in("ws").out(webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain](Fs2Streams[IO]))

  val wsServerEndpoint = wsEndpoint.serverLogicSuccess(_ =>
    IO.pure(in => in.evalMap(str => IO.println(s"responding with ${str.toUpperCase}") >> IO.pure(str.toUpperCase())))
  )
  // Just returning passed name with `Hello, ` prepended
  val helloWorldServerEndpoint = helloWorldEndpoint
    .serverLogic(name => IO.pure[Either[Unit, String]](Right(s"Hello, $name!")))

  private val declaredPort = 9090
  private val declaredHost = "localhost"

  // Creating handler for netty bootstrap
  override def run = NettyCatsServer
    .io()
    .use { server =>
      for {
        binding <- server
          .port(declaredPort)
          .host(declaredHost)
          .addEndpoints(List(wsServerEndpoint, helloWorldServerEndpoint))
          .start()
        result <- IO
          .fromFuture(IO.delay {
            val port = binding.port
            val host = binding.hostName
            println(s"Server started at port = ${binding.port}")
            import scala.concurrent.ExecutionContext.Implicits.global
            def useWebSocket(ws: WebSocket[Future]): Future[Unit] = {
              def send(i: Int) = ws.sendText(s"Hello $i!")
              def receive() = ws.receiveText().map(t => println(s"Client RECEIVED: $t"))
              for {
                _ <- send(1)
                _ <- receive()
                _ <- send(2)
                _ <- send(3)
                _ <- receive()
              } yield ()
            }
            val backend = PekkoHttpBackend()

            val url = uri"ws://$host:$port/ws"
            val allGood = uri"http://$host:$port/hello?name=Netty"
            basicRequest.response(asStringAlways).get(allGood).send(backend).map(r => println(r.body))
              .flatMap { _ =>
            basicRequest
              .response(asWebSocket(useWebSocket))
              .get(url)
              .send(backend)
              }
              .andThen { case _ => backend.close() }
          })
          .guarantee(binding.stop())
      } yield result
    }
}
