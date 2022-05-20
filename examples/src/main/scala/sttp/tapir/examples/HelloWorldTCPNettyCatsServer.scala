package sttp.tapir.examples

import cats.effect.IO
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend, UriContext, asStringAlways, basicRequest}
import sttp.model.StatusCode
import sttp.tapir.server.netty.{NettyCatsServer, NettyCatsServerBinding, NettyServerType}
import sttp.tapir.{PublicEndpoint, endpoint, query, stringBody}
import cats.effect.unsafe.implicits.global
import sttp.tapir.server.netty.NettyServerType._

object HelloWorldTCPNettyCatsServer extends App {
  // One endpoint on GET /hello with query parameter `name`
  val helloWorldEndpoint: PublicEndpoint[String, Unit, String, Any] =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody)

  // Just returning passed name with `Hello, ` prepended
  val helloWorldServerEndpoint = helloWorldEndpoint
    .serverLogic(name => IO.pure[Either[Unit, String]](Right(s"Hello, $name!")))

  private val declaredPort = 9090
  private val declaredHost = "localhost"

  // Creating handler for netty bootstrap
  val serverBinding =
    NettyCatsServer
      .io()
      .use { server =>

        val effect: IO[NettyCatsServerBinding[IO, TCP]] = server
          .port(declaredPort)
          .host(declaredHost)
          .addEndpoint(helloWorldServerEndpoint)
          .start()

        effect.map { binding =>

          val port = binding.port
          val host = binding.hostName
          println(s"Server started at port = ${binding.port}")

          val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
          val badUrl = uri"http://$host:$port/bad_url"
          assert(basicRequest.response(asStringAlways).get(badUrl).send(backend).code == StatusCode(404))

          val noQueryParameter = uri"http://$host:$port/hello"
          assert(basicRequest.response(asStringAlways).get(noQueryParameter).send(backend).code == StatusCode(400))

          val allGood = uri"http://$host:$port/hello?name=Netty"
          val body = basicRequest.response(asStringAlways).get(allGood).send(backend).body

          println("Got result: " + body)
          assert(body == "Hello, Netty!")
          assert(port == declaredPort, "Ports don't match")
          assert(host == declaredHost, "Hosts don't match")
        }
      }
      .unsafeRunSync()

}
