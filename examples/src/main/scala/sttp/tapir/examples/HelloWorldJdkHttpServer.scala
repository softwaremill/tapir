package sttp.tapir.examples

import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend, UriContext, asStringAlways, basicRequest}
import sttp.model.StatusCode
import sttp.tapir.server.jdkhttp._
import sttp.tapir.{PublicEndpoint, endpoint, query, stringBody}

object HelloWorldJdkHttpServer extends App {

  // One endpoint on GET /hello with query parameter `name`
  val helloWorldEndpoint: PublicEndpoint[String, Unit, String, Any] =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody)

  // Just returning passed name with `Hello, ` prepended
  val helloWorldServerEndpoint =
    helloWorldEndpoint.handle(name => Right(s"Hello, $name!"))

  private val declaredPort = 9090
  private val declaredHost = "localhost"

  // Starting jdk http server
  val server =
    JdkHttpServer()
      .port(declaredPort)
      .host(declaredHost)
      .addEndpoint(helloWorldServerEndpoint)
      .start()

  val port = server.getAddress.getPort
  val host = server.getAddress.getHostName

  println(s"Server started at port = $port")

  val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
  val badUrl = uri"http://$host:$port/bad_url"
  assert(basicRequest.response(asStringAlways).get(badUrl).send(backend).code == StatusCode(404))

  val noQueryParameter = uri"http://$host:$port/hello"
  assert(basicRequest.response(asStringAlways).get(noQueryParameter).send(backend).code == StatusCode(400))

  val allGood = uri"http://$host:$port/hello?name=Scala"
  val body = basicRequest.response(asStringAlways).get(allGood).send(backend).body

  println("Got result: " + body)
  assert(body == "Hello, Scala!")
  assert(port == declaredPort, "Ports don't match")
  assert(host == declaredHost, "Hosts don't match")

  server.stop(0)
}
