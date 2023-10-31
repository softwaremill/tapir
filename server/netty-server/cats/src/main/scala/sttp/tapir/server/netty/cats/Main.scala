package sttp.tapir.server.netty.cats

import cats.effect.IO
import sttp.tapir._
import cats.effect.syntax.all._
import cats.effect.unsafe.implicits.global
import sttp.tapir.server.netty.cats.{NettyCatsServer, NettyCatsServerBinding}
import cats.effect.IOApp
import scala.concurrent.duration._

object HelloWorldNettyCatsServer extends IOApp.Simple {
  // One endpoint on GET /hello with query parameter `name`
  val helloWorldEndpoint: PublicEndpoint[String, Unit, String, Any] =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody)

  // Just returning passed name with `Hello, ` prepended
  val helloWorldServerEndpoint = helloWorldEndpoint
    .serverLogic(name => IO.pure[Either[Unit, String]](Right(s"Hello, $name!")))

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
            .start
        }
      _ <- IO.sleep(3.seconds)
      _ <- runningServer.cancel
      _ <- IO.println("Server fiber cancellation requested")
    } yield ()

}
