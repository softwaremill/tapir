package sttp.tapir.server.netty.cats

import cats.effect.IO
import sttp.tapir._
import cats.effect.syntax.all._
import cats.effect.unsafe.implicits.global
import sttp.tapir.server.netty.cats.{NettyCatsServer, NettyCatsServerBinding}
import sttp.client4.httpclient.cats.HttpClientCatsBackend
import cats.effect.IOApp
import scala.concurrent.duration._
import sttp.client4._

object HelloWorldNettyCatsServer extends IOApp.Simple {
  // One endpoint on GET /hello with query parameter `name`
  val helloWorldEndpoint: PublicEndpoint[String, Unit, String, Any] =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody)

  // Just returning passed name with `Hello, ` prepended
  val helloWorldServerEndpoint = helloWorldEndpoint
    .serverLogic(name =>
      IO.println(s"=== Received request: $name") >> IO.sleep(6.seconds) >> IO.pure[Either[Unit, String]](Right(s"Hello, $name!"))
    )

  private val declaredPort = 9090
  private val declaredHost = "localhost"

  override def run =
    for {
      runningServer <- NettyCatsServer
        .io()
        .use { server =>
          server
            .port(declaredPort)
            .host(declaredHost)
            .addEndpoint(helloWorldServerEndpoint)
            .start()
            .bracket(binding => IO.println(">>>>>>>>>>>>>>>>>>> The app is now running... ") >> IO.never)(binding =>
              IO.println(">>>>>>>> Stopping binding ") >> binding.stop()
            )
        }
        .start
      _ <- IO.sleep(3.seconds)
      _ <- HttpClientCatsBackend
        .resource[IO]()
        .use { sttpBackend =>
          for {
            response <- basicRequest
              .get(uri"http://localhost:9090/hello?name=Bob")
              .send(sttpBackend)
            _ <- IO.println(response)
          } yield ()
        }
        .start // send http request in background
      _ <- IO.sleep(2.seconds)
      cancelation <- runningServer.cancel.start
      _ <- HttpClientCatsBackend
        .resource[IO]()
        .use { sttpBackend =>
          for {
            response <- basicRequest
              .get(uri"http://localhost:9090/hello?name=Cynthia")
              .send(sttpBackend)
            _ <- IO.println(response)
          } yield ()
        }
        .start // send http request in background
      _ <- IO.println("Server fiber cancellation requested")
      _ <- cancelation.join
    } yield ()

}
