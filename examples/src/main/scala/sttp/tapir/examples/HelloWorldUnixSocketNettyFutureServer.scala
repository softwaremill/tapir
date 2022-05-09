package sttp.tapir.examples

import sttp.tapir.server.netty.NettyFutureServer
import sttp.tapir.{PublicEndpoint, endpoint, query, stringBody}

import java.nio.file.Paths
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

/** @deprecated
  *   \- only for purpose of manual tests will be removed from final PR
  */
@deprecated("Only for development purpose")
object HelloWorldUnixSocketNettyFutureServer extends App {
  // One endpoint on GET /hello with query parameter `name`
  val helloWorldEndpoint: PublicEndpoint[String, Unit, String, Any] =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody)

  // Just returning passed name with `Hello, ` prepended
  val helloWorldServerEndpoint = helloWorldEndpoint
    .serverLogic(name => Future.successful[Either[Unit, String]](Right(s"Hello, $name!")))

  private val declaredPath = Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString)

  val serverBinding =
    Await.result(
      NettyFutureServer.unixDomainSocket
        .path(declaredPath)
        .addEndpoint(helloWorldServerEndpoint)
        .start(),
      Duration.Inf
    )

  // Bind and start to accept incoming connections.
  val path = serverBinding.path
  println(s"curl --unix-socket ${path} \"http://localhost/hello?name=John\" -vvv")

  assert(path == declaredPath.toString, "Paths not match")

  Thread.sleep(100000)
  Await.result(serverBinding.stop(), Duration.Inf)
}
