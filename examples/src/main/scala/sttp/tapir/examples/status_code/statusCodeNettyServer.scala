// {cat=Status code; effects=Direct; server=Netty}: Serving static files from a directory

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.8

package sttp.tapir.examples.status_code

import ox.supervised
import sttp.model.{HeaderNames, StatusCode}
import sttp.tapir.*
import sttp.tapir.server.netty.sync.NettySyncServer

/** Three examples of how to return custom status codes */
@main def statusCodeNettyServer(): Unit =

  // An endpoint which always responds with status code 308
  val fixedStatusCodeEndpoint = endpoint.get
    .in("fixed")
    .out(statusCode(StatusCode.PermanentRedirect))
    .out(header(HeaderNames.Location, "https://adopt-tapir.softwaremill.com"))
    .handleSuccess(_ => ())

  //

  // An endpoint which computes the status code to return as part of its server logic
  val dynamicStatusCodeEndpoint = endpoint.get
    .in("dynamic" / path[Int]("status_code"))
    .errorOut(stringBody)
    .out(statusCode)
    .out(stringBody)
    .handle(code =>
      StatusCode.safeApply(code) match {
        // by default, the status code for an error output is 400
        case Left(_) => Left(s"Unknown status code: $code")
        // by default, the status code for a successful output is 200; here, we are overriding it using the statusCode output
        case Right(parsedStatusCode) => Right((parsedStatusCode, s"Responding with status code: $code"))
      }
    )

  //

  sealed trait ErrorInfo
  case class NotFound(what: String) extends ErrorInfo
  case class Unauthorized(realm: String) extends ErrorInfo
  case object Unknown extends ErrorInfo

  // An endpoint which determines the status code basing on the type of the error output returned by the server logic (if any)
  val oneOfStatusCodeEndpoint = endpoint
    .in("oneof")
    .in(query[Int]("kind"))
    .errorOut(
      oneOf[ErrorInfo](
        oneOfVariant(statusCode(StatusCode.NotFound).and(stringBody.mapTo[NotFound])),
        oneOfVariant(statusCode(StatusCode.Unauthorized).and(stringBody.mapTo[Unauthorized])),
        oneOfDefaultVariant(emptyOutputAs(Unknown)) // by default, the status code is 400
      )
    )
    .handle {
      case 1 => Left(NotFound("not found")) // status code 404, as defined in oneOfVariant
      case 2 => Left(Unauthorized("secret realm")) // status code 401, as defined in oneOfVariant
      case 3 => Right(()) // status code 200
      case _ => Left(Unknown) // status code 400
    }

  //

  // Starting netty server
  val declaredPort = 8080
  val declaredHost = "localhost"
  supervised:
    val binding = NettySyncServer()
      .port(declaredPort)
      .host(declaredHost)
      .addEndpoints(List(fixedStatusCodeEndpoint, dynamicStatusCodeEndpoint, oneOfStatusCodeEndpoint))
      .start()

    // Bind and start to accept incoming connections.
    println(s"Server started at http://${binding.hostName}:${binding.port}")
    println("Press any key to stop")
    System.in.read()

    binding.stop()
