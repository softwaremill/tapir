// {cat=Streaming; effects=cats-effect; server=http4s}: Respond with an fs2 stream, or with an error, represented as a failed effect in the business logic

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-http4s-server:1.11.8
//> using dep org.http4s::http4s-blaze-server:0.23.16

package sttp.tapir.examples.streaming

import cats.effect.*
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext

object StreamingHttp4sFs2ServerOrError extends IOApp:
  case object UnknownUser extends Exception

  // the endpoint description
  def userDataEndpoint =
    endpoint.get
      .in("user" / path[String])
      .errorOut(statusCode(StatusCode.NotFound).and(emptyOutputAs(UnknownUser).description("no data found for user")))
      .out(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8)))

  // the tapir-independent business logic; if no user is found, returns a failed effect
  def lookupUserData(id: String): IO[fs2.Stream[IO, Byte]] =
    if id == "existing_user" then IO.pure(fs2.Stream.fromIterator[IO]("user data".getBytes.iterator, 16)) else IO.raiseError(UnknownUser)

  // combining the endpoint description with the server logic; we need to provide a
  // String => IO[Either[UnknownUser, fs2.Stream[IO, Byte]]] function
  def userDataServerEndpoint = userDataEndpoint.serverLogic { id =>
    val value = lookupUserData(id)

    value
      .map(stream => Right(stream))
      .recoverWith { case UnknownUser =>
        IO(println("error encountered")).map(_ => Left(UnknownUser))
      }
  }

  val userDataRoutes: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(userDataServerEndpoint)

  // Test with:
  // curl -v http://localhost:8080/user/existing_user (responds with 200)
  // curl -v http://localhost:8080/user/another_user (responds with 404)
  override def run(args: List[String]): IO[ExitCode] =
    // starting the server
    BlazeServerBuilder[IO]
      .withExecutionContext(scala.concurrent.ExecutionContext.global)
      .bindHttp(8080, "localhost")
      .withHttpApp(Router("/" -> userDataRoutes).orNotFound)
      .resource
      .use { _ => IO.never }
      .as(ExitCode.Success)
