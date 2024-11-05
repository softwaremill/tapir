// {cat=Custom types; effects=Direct; server=Netty; docs=Swagger UI}: Handling comma-separated query parameters

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-swagger-ui-bundle:1.11.8

package sttp.tapir.examples.custom_types

import ox.supervised
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.model.{CommaSeparated, Delimited}
import sttp.tapir.server.netty.sync.NettySyncServer
import sttp.tapir.swagger.bundle.SwaggerInterpreter

enum TapirBreeds(val name: String):
  case CentralAmerican extends TapirBreeds("Central American")
  case SouthAmerican extends TapirBreeds("South American")
  case Mountain extends TapirBreeds("Mountain")
  case Malayan extends TapirBreeds("Malayan")

  override def toString: String = name

/*
  A simple example that showcases how to use a custom `enum` as a comma-separated query parameter.

  We're going to build a simple endpoint capable of accepting requests such as
    `GET tapirs?breeds=Malayan,CentralAmerican`
 */
@main def commaSeparatedQueryParameter(): Unit =
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

  given Codec[String, TapirBreeds, TextPlain] = Codec.derivedEnumeration[String, TapirBreeds].defaultStringBased

  val echoTapirs: Endpoint[Unit, CommaSeparated[TapirBreeds], Unit, String, Any] = endpoint.get
    .in("tapirs")
    .in(
      query[CommaSeparated[TapirBreeds]]("breeds")
        // If we want the filter to be optional, we need either `default` or to make to wrap the parameter in `Optional`
        .default(Delimited[",", TapirBreeds](TapirBreeds.values.toList))
    )
    .out(stringBody)

  val echoTapirsServerEndpoint =
    echoTapirs.handleSuccess(breeds => s"Tapir breeds: ${breeds.values.mkString(", ")}")

  ////////////////////////////////// Boilerplate: starting up server and swagger UI //////////////////////////////////

  val docsEndpoints = SwaggerInterpreter().fromServerEndpoints[Identity](List(echoTapirsServerEndpoint), "Echo", "1.0.0")

  supervised {
    val binding = NettySyncServer()
      .port(8080)
      .host("localhost")
      .addEndpoints(echoTapirsServerEndpoint :: docsEndpoints)
      .start()

    println(s"Go to: http://${binding.hostName}:${binding.port}/docs")
    println("Press any key to exit ...")
    scala.io.StdIn.readLine()

    binding.stop()
  }
