// {cat=Security; effects=Future; server=Vert.x}: CORS interceptor

//> using dep com.softwaremill.sttp.tapir::tapir-vertx-server:1.12.0
//> using dep com.softwaremill.sttp.client4::core:4.0.0-RC3

package sttp.tapir.examples.security

import io.vertx.core.Vertx
import io.vertx.ext.web.*
import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.model.headers.Origin
import sttp.model.{Header, HeaderNames, Method, StatusCode}
import sttp.tapir.*
import sttp.tapir.server.interceptor.cors.{CORSConfig, CORSInterceptor}
import sttp.tapir.server.vertx.VertxFutureServerInterpreter.*
import sttp.tapir.server.vertx.{VertxFutureServerInterpreter, VertxFutureServerOptions}

import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future}

@main def corsInterceptorVertxServer() =
  given ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  val vertx = Vertx.vertx()

  val server = vertx.createHttpServer()
  val router = Router.router(vertx)

  val myEndpoint = endpoint.get
    .in("path")
    .out(plainBody[String])
    .serverLogic(_ => Future(Right("OK")))

  val corsInterceptor = VertxFutureServerOptions.customiseInterceptors
    .corsInterceptor(
      CORSInterceptor.customOrThrow(
        CORSConfig.default
          .allowOrigin(Origin.Host("http", "my.origin"))
          .allowMethods(Method.GET)
      )
    )
    .options

  val attach = VertxFutureServerInterpreter(corsInterceptor).route(myEndpoint)
  val _ = attach(router)

  // starting the server
  val bindAndCheck = server.requestHandler(router).listen(9000).asScala.map { binding =>
    val backend = HttpClientSyncBackend()

    // Sending preflight request with allowed origin
    val preflightResponse = basicRequest
      .options(uri"http://localhost:9000/path")
      .headers(
        Header.origin(Origin.Host("http", "my.origin")),
        Header.accessControlRequestMethod(Method.GET)
      )
      .send(backend)

    assert(preflightResponse.code == StatusCode.NoContent)
    assert(preflightResponse.headers.contains(Header.accessControlAllowOrigin("http://my.origin")))
    assert(preflightResponse.headers.contains(Header.accessControlAllowMethods(Method.GET)))

    println("Got expected response for preflight request")

    // Sending preflight request with disallowed origin
    val preflightResponseForDisallowedOrigin = basicRequest
      .options(uri"http://localhost:9000/path")
      .headers(
        Header.origin(Origin.Host("http", "disallowed.com")),
        Header.accessControlRequestMethod(Method.GET)
      )
      .send(backend)

    // Check response does not contain allowed origin header
    assert(preflightResponseForDisallowedOrigin.code == StatusCode.NoContent)
    assert(!preflightResponseForDisallowedOrigin.headers.contains(Header.accessControlAllowOrigin("http://example.com")))

    println("Got expected response for preflight request for wrong origin. No allowed origin header in response")

    // Sending regular request from allowed origin
    val requestResponse = basicRequest
      .response(asStringAlways)
      .get(uri"http://localhost:9000/path")
      .headers(Header.origin(Origin.Host("http", "my.origin")))
      .send(backend)

    assert(requestResponse.code == StatusCode.Ok)
    assert(requestResponse.body == "OK")
    assert(requestResponse.headers.contains(Header.vary(HeaderNames.Origin)))
    assert(requestResponse.headers.contains(Header.accessControlAllowOrigin("http://my.origin")))

    println("Got expected response for regular request")

    binding
  }

  val _ = Await.result(bindAndCheck.flatMap(_.close().asScala), 1.minute)
