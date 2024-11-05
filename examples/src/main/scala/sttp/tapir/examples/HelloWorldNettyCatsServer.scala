// {cat=Hello, World!; effects=cats-effect; server=Netty}: Exposing an endpoint using the Netty server (cats-effect variant)

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-cats:1.11.8
//> using dep com.softwaremill.sttp.client3::core:3.9.8
//> using dep ch.qos.logback:logback-classic:1.5.8

package sttp.tapir.examples

import cats.effect.{IO, IOApp}
import sttp.client3.{HttpURLConnectionBackend, SttpBackend, UriContext, asStringAlways, basicRequest}
import sttp.model.StatusCode
import sttp.shared.Identity
import sttp.tapir.server.netty.cats.NettyCatsServer
import sttp.tapir.*

object HelloWorldNettyCatsServer extends IOApp.Simple:
  // One endpoint on GET /hello with query parameter `name`
  val helloWorldEndpoint: PublicEndpoint[String, Unit, String, Any] =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody)

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
          .addEndpoint(helloWorldServerEndpoint)
          .start()
        result <- IO
          .blocking {
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
          .guarantee(binding.stop())
      } yield result
    }
