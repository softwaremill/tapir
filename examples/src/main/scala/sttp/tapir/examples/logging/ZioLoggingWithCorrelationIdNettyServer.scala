// {cat=Logging; effects=ZIO; server=Netty}: Logging using a correlation id

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-zio:1.11.8
//> using dep com.softwaremill.sttp.client3::zio:3.9.8

package sttp.tapir.examples.logging

import sttp.client3.httpclient.zio.HttpClientZioBackend
import sttp.client3.{UriContext, basicRequest}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.RequestInterceptor.RequestResultEffectTransform
import sttp.tapir.server.interceptor.{RequestInterceptor, RequestResult}
import sttp.tapir.server.netty.zio.{NettyZioServer, NettyZioServerOptions}
import sttp.tapir.ztapir.*
import zio.{ExitCode, Task, URIO, ZIO, ZIOAppDefault, durationInt}

object ZioLoggingWithCorrelationIdNettyServer extends ZIOAppDefault:
  val CorrelationIdHeader = "X-Correlation-Id"

  // An endpoint with some logging added
  val loggingEndpoint: ZServerEndpoint[Any, Any] =
    endpoint.get.in("hello").in(query[String]("name")).zServerLogic { name =>
      for {
        _ <- ZIO.log(s"Starting computation for: $name ...")
        fiber <- (ZIO.sleep(100.millis) *> ZIO.log(s"Saying hello to: $name")).fork
        _ <- ZIO.log(s"Bye, $name!")
        _ <- fiber.join
      } yield ()
    }

  val correlationIdInterceptor = RequestInterceptor.transformResultEffect(new RequestResultEffectTransform[Task] {
    override def apply[B](request: ServerRequest, result: Task[RequestResult[B]]): Task[RequestResult[B]] = {
      val cid = request.header(CorrelationIdHeader).getOrElse("NO-CID")
      ZIO.logAnnotate("cid", cid)(result)
    }
  })

  override def run: URIO[Any, ExitCode] =
    val serverOptions = NettyZioServerOptions.customiseInterceptors.prependInterceptor(correlationIdInterceptor).options
    (for {
      binding <- NettyZioServer(serverOptions).port(8080).addEndpoint(loggingEndpoint).start()
      httpClient <- HttpClientZioBackend()
      _ <- httpClient.send(basicRequest.get(uri"http://localhost:8080/hello?name=Emma").header(CorrelationIdHeader, "ABC-123"))
      _ <- httpClient.send(basicRequest.get(uri"http://localhost:8080/hello?name=Bob"))
      _ <- binding.stop()
    } yield ()).exitCode
