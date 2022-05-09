package sttp.tapir.examples

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import sttp.tapir.server.netty.{NettyCatsServer, NettyCatsServerBinding, NettyServerType}
import sttp.tapir.{PublicEndpoint, endpoint, query, stringBody}

import java.nio.file.Paths
import java.util.UUID

/** @deprecated
  *   \- only for purpose of manual tests will be removed from final PR
  */
@deprecated("Only for development purpose")
object HelloWorldUnixSocketNettyCatsServer extends App {
  // One endpoint on GET /hello with query parameter `name`
  val helloWorldEndpoint: PublicEndpoint[String, Unit, String, Any] =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody)

  // Just returning passed name with `Hello, ` prepended
  val helloWorldServerEndpoint = helloWorldEndpoint
    .serverLogic(name => IO.pure[Either[Unit, String]](Right(s"Hello, $name!")))

  private val declaredPath = Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString)

  Dispatcher[IO]
    .map(NettyCatsServer.unixDomainSocket(_))
    .use { server =>

      val effect: IO[NettyCatsServerBinding[IO, NettyServerType.UnixSocket]] = server
        .path(declaredPath)
        .addEndpoint(helloWorldServerEndpoint)
        .start()

      effect.map { binding =>

        // Bind and start to accept incoming connections.
        val path = binding.path
        println(s"curl --unix-socket ${path} \"http://localhost/hello?name=John\" -vvv")

        assert(path == declaredPath.toString, "Paths not match")
        Thread.sleep(1000000)
      }
    }
    .unsafeRunSync()

}
