// {cat=Security; effects=Future; server=Vert.x}: CORS interceptor

//> using dep com.softwaremill.sttp.tapir::tapir-vertx-server:1.11.11
//> using dep com.softwaremill.sttp.client3::core:3.10.2

package sttp.tapir.examples.security

import io.vertx.core.Vertx
import io.vertx.ext.web.*
import io.vertx.ext.web.handler.CorsHandler
import sttp.client3.UriContext
import sttp.tapir.*
import sttp.tapir.server.interceptor.cors.CORSInterceptor
import sttp.tapir.server.vertx.VertxFutureServerInterpreter.*
import sttp.tapir.server.vertx.{VertxFutureServerInterpreter, VertxFutureServerOptions}
import sttp.client3.*
import sttp.model.{Header, HeaderNames, Method, StatusCode}

import sttp.model.headers.Origin
import sttp.tapir.*
import sttp.tapir.server.interceptor.cors.{CORSConfig, CORSInterceptor}


import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future}

@main def corsInterceptorVertxServer() =
  given ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  val vertx = Vertx.vertx()

  val server = vertx.createHttpServer()
  val router = Router.router(vertx)

  // CORS works with native Vert.x handler
//  router.route().handler(CorsHandler.create())

  val myEndpoint = endpoint.get
    .in("path")
    .out(plainBody[String])
    .serverLogic(_ => Future(Right("OK")))

  val corsInterceptor = VertxFutureServerOptions.customiseInterceptors
    .corsInterceptor(
      CORSInterceptor.default[Future]
    ).options

  val attach = VertxFutureServerInterpreter(corsInterceptor).route(myEndpoint)
  attach(router)

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

    println(s"Preflight response code: ${preflightResponse.code}")
    println(s"Preflight response headers: ${preflightResponse.headers}")

    assert(preflightResponse.code == StatusCode.NoContent)

    println("Got expected response for preflight request")

    binding
  }

  Await.result(bindAndCheck.flatMap(_.close().asScala), 1.minute)
