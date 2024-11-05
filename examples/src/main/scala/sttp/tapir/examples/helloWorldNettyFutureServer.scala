// {cat=Hello, World!; effects=Future; server=Netty}: Exposing an endpoint using the Netty server (Future variant)

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server:1.11.8
//> using dep com.softwaremill.sttp.client3::core:3.9.8
//> using dep ch.qos.logback:logback-classic:1.5.8

package sttp.tapir.examples

import sttp.client3.{HttpURLConnectionBackend, SttpBackend, UriContext, asStringAlways, basicRequest}
import sttp.model.StatusCode
import sttp.shared.Identity
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerBinding}
import sttp.tapir.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

@main def helloWorldNettyFutureServer(): Unit =
  // One endpoint on GET /hello with query parameter `name`
  val helloWorldEndpoint: PublicEndpoint[String, Unit, String, Any] =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody)

  // Just returning passed name with `Hello, ` prepended
  val helloWorldServerEndpoint = helloWorldEndpoint
    .serverLogic(name => Future.successful[Either[Unit, String]](Right(s"Hello, $name!")))

  val declaredPort = 9090
  val declaredHost = "localhost"

  // Starting netty server
  val serverBinding: NettyFutureServerBinding =
    Await.result(
      NettyFutureServer()
        .port(declaredPort)
        .host(declaredHost)
        .addEndpoint(helloWorldServerEndpoint)
        .start(),
      Duration.Inf
    )

  // Bind and start to accept incoming connections.
  val port = serverBinding.port
  val host = serverBinding.hostName
  println(s"Server started at port = ${serverBinding.port}")

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

  Await.result(serverBinding.stop(), Duration.Inf)
