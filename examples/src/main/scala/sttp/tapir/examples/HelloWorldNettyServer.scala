package sttp.tapir.examples

import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend, UriContext, asStringAlways, basicRequest}
import sttp.model.StatusCode
import sttp.tapir.server.netty.{NettyFutureServer}
import sttp.tapir.{PublicEndpoint, endpoint, query, stringBody}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object HelloWorldNettyServer extends App {
  // One endpoint on GET /hello with query parameter `name`
  val helloWorldEndpoint: PublicEndpoint[String, Unit, String, Any] =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody)

  // Just returning passed name with `Hello, ` prepended
  val helloWorldServerEndpoint = helloWorldEndpoint
    .serverLogic(name => Future.successful[Either[Unit, String]](Right(s"Hello, $name!")))

  private val declaredPort = 9090
  private val declaredHost = "localhost"
  // Creating handler for netty bootstrap
  val serverBinding =
    Await.result(
      NettyFutureServer.tcp
        .port(declaredPort)
        .host(declaredHost)
        .addEndpoint(helloWorldServerEndpoint)
        .start(),
      Duration.Inf
    )

  // Bind and start to accept incoming connections.
  val port = serverBinding.localSocket.getPort
  val host = serverBinding.localSocket.getHostName
  println(s"Server started at port = ${serverBinding.localSocket.getPort}")

  val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
  val badUrl = uri"http://$host:$port/bad_url"
  assert(basicRequest.response(asStringAlways).get(badUrl).send(backend).code == StatusCode(404))

  val noQueryParameter = uri"http://$host:$port/hello"
  assert(basicRequest.response(asStringAlways).get(noQueryParameter).send(backend).code == StatusCode(400))

  val allGood = uri"http://$host:$port/hello?name=Netty"
  val body = basicRequest.response(asStringAlways).get(allGood).send(backend).body

  println("Got result: " + body)
  assert(body == "Hello, Netty!")
  assert(port == declaredPort, "Ports not match")
  assert(host == declaredHost, "Hosts not match")

  Await.result(serverBinding.stop(), Duration.Inf)
}
