// {cat=Security; effects=Future; server=Pekko HTTP}: CORS interceptor

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-pekko-http-server:1.11.8
//> using dep com.softwaremill.sttp.client3::core:3.9.8

package sttp.tapir.examples.security

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Route
import sttp.client3.*
import sttp.model.{Header, HeaderNames, Method, StatusCode}

import sttp.model.headers.Origin
import sttp.tapir.*
import sttp.tapir.server.interceptor.cors.{CORSConfig, CORSInterceptor}
import sttp.tapir.server.pekkohttp.*

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

@main def corsInterceptorPekkoServer(): Unit =
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  val helloCors: Endpoint[Unit, String, Unit, String, Any] = endpoint.get.in("hello").in(stringBody).out(stringBody)

  // Add CORSInterceptor to ServerOptions. Allow http://example.com origin, GET methods, credentials and some custom
  // headers.
  val customServerOptions: PekkoHttpServerOptions = PekkoHttpServerOptions.customiseInterceptors
    .corsInterceptor(
      CORSInterceptor.customOrThrow(
        CORSConfig.default
          .allowOrigin(Origin.Host("http", "example.com"))
          .allowMethods(Method.GET)
          .allowHeaders("X-Foo", "X-Bar")
          .allowCredentials
          .maxAge(42.seconds)
      )
    )
    .options

  val helloCorsRoute: Route =
    PekkoHttpServerInterpreter(customServerOptions).toRoute(helloCors.serverLogicSuccess(name => Future.successful(s"Hello!")))

  // starting the server
  val bindAndCheck = Http().newServerAt("localhost", 8080).bindFlow(helloCorsRoute).map { binding =>
    val backend = HttpClientSyncBackend()

    // Sending preflight request with allowed origin
    val preflightResponse = basicRequest
      .options(uri"http://localhost:8080/hello")
      .headers(
        Header.origin(Origin.Host("http", "example.com")),
        Header.accessControlRequestMethod(Method.GET)
      )
      .send(backend)

    assert(preflightResponse.code == StatusCode.NoContent)
    assert(preflightResponse.headers.contains(Header.accessControlAllowOrigin("http://example.com")))
    assert(preflightResponse.headers.contains(Header.accessControlAllowMethods(Method.GET)))
    assert(preflightResponse.headers.contains(Header.accessControlAllowCredentials(true)))
    assert(preflightResponse.headers.contains(Header.accessControlMaxAge(42)))

    println("Got expected response for preflight request")

    // Sending preflight request with not allowed origin
    val preflightResponseForUnallowedOrigin = basicRequest
      .options(uri"http://localhost:8080/hello")
      .headers(
        Header.origin(Origin.Host("http", "unallowed.com")),
        Header.accessControlRequestMethod(Method.GET)
      )
      .send(backend)

    // Check response does not contain allowed origin header
    assert(preflightResponseForUnallowedOrigin.code == StatusCode.NoContent)
    assert(!preflightResponseForUnallowedOrigin.headers.contains(Header.accessControlAllowOrigin("http://example.com")))

    println("Got expected response for preflight request for wrong origin. No allowed origin header in response")

    // Sending regular request from allowed origin
    val requestResponse = basicRequest
      .response(asStringAlways)
      .get(uri"http://localhost:8080/hello")
      .headers(Header.origin(Origin.Host("http", "example.com")), Header.authorization("Bearer", "dummy-credentials"))
      .send(backend)

    assert(requestResponse.code == StatusCode.Ok)
    assert(requestResponse.body == "Hello!")
    assert(requestResponse.headers.contains(Header.vary(HeaderNames.Origin)))
    assert(requestResponse.headers.contains(Header.accessControlAllowOrigin("http://example.com")))
    assert(requestResponse.headers.contains(Header.accessControlAllowCredentials(true)))

    println("Got expected response for regular request")

    binding
  }

  Await.result(bindAndCheck.flatMap(_.terminate(1.minute)), 1.minute)
