package sttp.tapir.examples

import sttp.client3.{HttpURLConnectionBackend, Identity, Response, SttpBackend, UriContext, asStringAlways, basicRequest}
import sttp.model.StatusCode
import sttp.tapir.server.jdkhttp.*
import sttp.tapir.*

object HelloWorldJdkHttpServer extends App {

  // GET /hello endpoint, with query parameter `name`
  val helloWorldEndpoint: PublicEndpoint[String, Unit, String, Any] =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody)

  val secondEndpoint: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint.get.in("second").out(stringBody)

  // Providing the server logic for the endpoints: here, just returning the passed name with `Hello, ` prepended
  val helloWorldServerEndpoint = helloWorldEndpoint.handle(name => Right(s"Hello, $name!"))

  val secondServerEndpoint = secondEndpoint.handle(_ => Right("IT WORKS!"))

  private val declaredPort = 9090
  private val declaredHost = "localhost"

  // Starting jdk http server
  val server =
    JdkHttpServer()
      .port(declaredPort)
      .host(declaredHost)
      .addEndpoint(helloWorldServerEndpoint)
      .addEndpoint(secondServerEndpoint)
      .start()

  val port = server.getAddress.getPort
  val host = server.getAddress.getHostName

  println(s"Server started at port = $port")

  try {
    val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
    val badUrl = uri"http://$host:$port/bad_url"
    assert(basicRequest.response(asStringAlways).get(badUrl).send(backend).code == StatusCode(404))

    val noQueryParameter = uri"http://$host:$port/hello"
    assert(basicRequest.response(asStringAlways).get(noQueryParameter).send(backend).code == StatusCode(400))

    val second = uri"http://$host:$port/second"
    val secondResponse: Response[String] = basicRequest.response(asStringAlways).get(second).send(backend)
    assert(secondResponse.code == StatusCode(200), "Status code returned from /second endpoint is not 200!")
    assert(secondResponse.body == "IT WORKS!", s"Body returned from /second endpoint is wrong: ${secondResponse.body}")

    val allGood = uri"http://$host:$port/hello?name=Scala"
    val body = basicRequest.response(asStringAlways).get(allGood).send(backend).body

    println("Got result: " + body)
    assert(body == "Hello, Scala!")
    assert(port == declaredPort, "Ports don't match")
    assert(host == declaredHost, "Hosts don't match")
  } finally {
    server.stop(0)
  }
}
