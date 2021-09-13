package sttp.tapir.examples

import java.lang

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend, UriContext, asStringAlways, basicRequest}
import sttp.model.StatusCode
import sttp.tapir.server.netty.NettyServerInterpreter
import sttp.tapir.server.netty.NettyServerInterpreter.Route
import sttp.tapir.server.netty.example.NettyServerInitializer
import sttp.tapir.{Endpoint, endpoint, query, stringBody}

object HelloWorldNettyServer extends App {
  //One endpoint on GET /hello with query parameter `name`
  val helloWorldEndpoint: Endpoint[String, Unit, String, Any] = {
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody)
  }

  //Just returning passed name with `Hello, ` prepended
  val helloWorldLogic = helloWorldEndpoint
    .serverLogic(name => Future.successful[Either[Unit, String]](Right(s"Hello, $name!")))

  // Creating handler for netty bootstrap
  val nettyRequestHandler = NettyServerInterpreter().toHandler(List(helloWorldLogic))

  val httpBootstrap = createNettyServerBootstrap(nettyRequestHandler)

  val port = 8888

  // Bind and start to accept incoming connections.
  val httpChannel = httpBootstrap.bind(port).sync
  println(s"Server started at port = ${port}")

  val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
  val badUrl = uri"http://localhost:8888/bad_url"
  assert(basicRequest.response(asStringAlways).get(badUrl).send(backend).code == StatusCode(404))

  val noQueryParameter = uri"http://localhost:8888/hello"
  assert(basicRequest.response(asStringAlways).get(noQueryParameter).send(backend).code == StatusCode(400))

  val allGood = uri"http://localhost:8888/hello?name=Netty"
  val body = basicRequest.response(asStringAlways).get(allGood).send(backend).body

  println("Got result: " + body)
  assert(body == "Hello, Netty!")

  private def createNettyServerBootstrap(handler: Route) = {
    val httpBootstrap = new ServerBootstrap()
    val eventLoopGroup = new NioEventLoopGroup()

    httpBootstrap
      .group(eventLoopGroup)
      .channel(classOf[NioServerSocketChannel])
      .childHandler(new NettyServerInitializer(List(nettyRequestHandler)))
      .option[Integer](ChannelOption.SO_BACKLOG, 128)
      .childOption[lang.Boolean](ChannelOption.SO_KEEPALIVE, true)
  }
}
