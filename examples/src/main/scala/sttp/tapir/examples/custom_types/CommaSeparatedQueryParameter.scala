package sttp.tapir.examples.custom_types

import sttp.tapir.*
import sttp.tapir.model.{CommaSeparated, Delimited}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerBinding}
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

/*
  A simple example that showcases how to use a custom Enumeration as a comma-separated query parameter.

  We're going to build a simple endpoint capable of accepting requests such as
    `GET tapirs?breeds=Malayan,CentralAmerican`

  Warning: are you using Scala 2.12 or older? Then you need to write your own `Delimited`, as the one provided
  unfortunately only works with Scala 2.13+.
 */
object CommaSeparatedQueryParameter extends App {

  /*
    It's possible to use `CommaSeparated` with any type with a `Codec[String, T, CodecFormat.TextPlain]` instance.

    That means, for example
      - scala.Enumeration, as in this example
      - sealed families (ADTs)
      - scala 3 enums
      - `Enumeratum` enumerations
      - etc.

    That codec will also determine validation rules, e.g. if the input should be case-insensitive or not.

    See also:
      - https://tapir.softwaremill.com/en/latest/endpoint/enumerations.html
      - https://tapir.softwaremill.com/en/latest/endpoint/integrations.html#enumeratum-integration
   */
  object TapirBreeds extends Enumeration {
    type Breed = Value

    val CentralAmerican: Breed = Value("Central American")
    val SouthAmerican: Breed = Value("South American")
    val Mountain: Breed = Value("Mountain")
    val Malayan: Breed = Value("Malayan")
  }

  val echoTapirs: Endpoint[Unit, CommaSeparated[TapirBreeds.Breed], Unit, String, Any] = endpoint.get
    .in("tapirs")
    .in(
      query[CommaSeparated[TapirBreeds.Breed]]("breeds")
        // If we want the filter to be optional, we need either `default` or to make to wrap the parameter in `Optional`
        .default(Delimited[",", TapirBreeds.Breed](TapirBreeds.values.toList))
    )
    .out(stringBody)

  val echoTapirsServerEndpoint: ServerEndpoint[Any, Future] =
    echoTapirs.serverLogicSuccess[Future](breeds => Future.successful(s"Tapir breeds: ${breeds.values.mkString(", ")}"))

  ////////////////////////////////// Boilerplate: starting up server and swagger UI //////////////////////////////////

  val docsEndpoints = SwaggerInterpreter().fromServerEndpoints[Future](List(echoTapirsServerEndpoint), "Echo", "1.0.0")

  val serverBinding: NettyFutureServerBinding =
    Await.result(
      NettyFutureServer()
        .port(8080)
        .host("localhost")
        .addEndpoints(echoTapirsServerEndpoint :: docsEndpoints)
        .start(),
      Duration.Inf
    )

  println(s"Go to: http://${serverBinding.hostName}:${serverBinding.port}/docs")
  println("Press any key to exit ...")
  scala.io.StdIn.readLine()

  Await.result(serverBinding.stop(), Duration.Inf)
}
