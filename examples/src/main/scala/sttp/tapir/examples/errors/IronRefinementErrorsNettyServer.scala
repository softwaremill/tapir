// {cat=Error handling; effects=cats-effect; server=Netty; JSON=circe}: Error reporting provided by Iron type refinements

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-cats:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-iron:1.11.8
//> using dep com.softwaremill.sttp.client3::core:3.9.8

package sttp.tapir.examples.errors

import cats.effect.{IO, IOApp}
import io.github.iltotore.iron.constraint.string.Match
import io.github.iltotore.iron.constraint.numeric.Positive
import io.github.iltotore.iron.:|
import io.github.iltotore.iron.autoRefine
import io.github.iltotore.iron.{Constraint, refineEither}
import io.circe.generic.auto.*
import io.circe.{Decoder, Encoder}
import sttp.client3.*
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.DecodeResult.Error
import sttp.tapir.DecodeResult.Error.JsonDecodeException
import sttp.tapir.server.interceptor.DecodeFailureContext
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler.FailureMessages
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureInterceptor, DefaultDecodeFailureHandler}

import sttp.client3.{HttpURLConnectionBackend, SttpBackend, UriContext, asStringAlways, basicRequest}
import sttp.model.StatusCode
import sttp.tapir.server.netty.cats.NettyCatsServer
import sttp.tapir.json.circe.*
import sttp.tapir.codec.iron.given
import sttp.tapir.generic.auto.*

object IronRefinementErrorsNettyServer extends IOApp.Simple:

  case class IronException(error: String) extends Exception(error)

  type Guard = Match["^[A-Z][a-z]+$"]
  type Age = Int :| Positive

  case class Person(name: String, age: Age)

  inline given Encoder[Age] = summon[Encoder[Int]].contramap(_.asInstanceOf[Int])

  // Decoder throwing custom exception when refinement fails
  inline given (using inline constraint: Constraint[Int, Positive]): Decoder[Age] = summon[Decoder[Int]].map(unrefinedValue =>
    unrefinedValue.refineEither[Positive] match
      case Right(value)       => value
      case Left(errorMessage) => throw IronException(s"Could not refine value $unrefinedValue: $errorMessage")
  )

  val addPerson: PublicEndpoint[Person, String, String, Any] = endpoint.post
    .in("add")
    .in(
      jsonBody[Person]
        .description("The person to add")
        .example(Person("Warski", 30))
    )
    .errorOut(stringBody)
    .out(stringBody)

  val addPersonServerEndpoint = addPerson
    .serverLogic[IO](person => IO.pure[Either[String, String]](Right(s"It's OK! Got $person")))

  // Handle failure, when error contains custom exception it means iron refinement failed
  // and we can add the failure details to the error message.
  private def failureDetailMessage(failure: DecodeResult.Failure): Option[String] = failure match {
    case Error(_, JsonDecodeException(_, IronException(errorMessage))) => Some(errorMessage)
    case Error(_, IronException(errorMessage))                         => Some(errorMessage)
    case other                                                         => FailureMessages.failureDetailMessage(other)
  }

  private def failureMessage(ctx: DecodeFailureContext): String = {
    val base = FailureMessages.failureSourceMessage(ctx.failingInput)
    val detail = failureDetailMessage(ctx.failure)
    FailureMessages.combineSourceAndDetail(base, detail)
  }

  def ironFailureHandler[T[_]] = new DefaultDecodeFailureHandler[T](
    DefaultDecodeFailureHandler.respond,
    failureMessage,
    DefaultDecodeFailureHandler.failureResponse
  )

  // Interceptor
  def ironDecodeFailureInterceptor[T[_]] = new DecodeFailureInterceptor[T](ironFailureHandler[T])

  private val declaredPort = 9090
  private val declaredHost = "localhost"

  override def run: IO[Unit] = NettyCatsServer
    .io()
    .use { server =>
      // Don't forget to add the interceptor to server options
      val optionsWithInterceptor = server.options.prependInterceptor(ironDecodeFailureInterceptor)
      for {
        binding <- server
          .port(declaredPort)
          .host(declaredHost)
          .options(optionsWithInterceptor)
          .addEndpoint(addPersonServerEndpoint)
          .start()
        result <- IO
          .blocking {
            val port = binding.port
            val host = binding.hostName

            val url = uri"http://$host:$port/add"
            println(s"Server started at port = $port")

            assert(port == declaredPort, "Ports don't match")
            assert(host == declaredHost, "Hosts don't match")

            val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

            println("Sending valid request")

            val validPersonJson = """{ "name": "Warski", "age": 25 }"""
            val body = basicRequest.response(asStringAlways).post(url).body(validPersonJson).send(backend).body
            println(s"Response: $body")

            println("Sending invalid request")
            val invalidPersonJson = """{ "name": "Warski", "age": -1 }"""
            val response = basicRequest.response(asStringAlways).post(url).body(invalidPersonJson).send(backend)
            println(s"Response status ${response.code}, body: ${response.body}")
            assert(response.code == StatusCode(400))
            // Iron refinement failed - details should be received in response body
            assert(response.body == "Invalid value for: body (Could not refine value -1: Should be strictly positive)")
          }
          .guarantee(binding.stop())
      } yield result
    }
