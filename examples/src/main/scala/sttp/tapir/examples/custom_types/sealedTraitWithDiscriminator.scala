// {cat=Custom types; effects=Direct; server=Netty; JSON=circe; docs=Swagger UI}: Mapping a sealed trait hierarchy to JSON using a discriminator

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-swagger-ui-bundle:1.11.8

package sttp.tapir.examples.custom_types

import io.circe.Codec as CirceCodec
import io.circe.derivation.Configuration as CirceConfiguration
import ox.supervised
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.generic.Configuration
import sttp.tapir.json.circe.*
import sttp.tapir.server.netty.sync.NettySyncServer
import sttp.tapir.swagger.bundle.SwaggerInterpreter

@main def sealedTraitWithDiscriminator(): Unit =
  // data structures
  sealed trait Node
  case class Leaf(value: String) extends Node
  case class Branch(name: String, children: Seq[Node]) extends Node

  val discriminatorFieldName = "kind"

  // these configs must match: one configures tapir's schema, the other circe's encoding/decoding to/from json
  given Configuration = Configuration.default.withDiscriminator(discriminatorFieldName)
  // the CirceConfiguration class is a renamed import (see above), as the name would clash with tapir's Configuration
  given CirceConfiguration = CirceConfiguration.default.withDiscriminator(discriminatorFieldName)

  given CirceCodec[Node] = CirceCodec.AsObject.derivedConfigured
  given Schema[Node] = Schema.derived

  // endpoint description; the configs are used when deriving the Schema, Encoder and Decoder, which are implicit
  // parameters to jsonBody[Node]
  val nodesListing: PublicEndpoint[Unit, Unit, Node, Any] = endpoint.get
    .in("nodes")
    .out(jsonBody[Node])

  val nodesListingServerEndpoint =
    nodesListing.handleSuccess(_ => Branch("b", List(Leaf("x"), Leaf("y"))))

  val docsEndpoints = SwaggerInterpreter().fromServerEndpoints[Identity](List(nodesListingServerEndpoint), "Nodes", "1.0.0")

  supervised {
    val binding = NettySyncServer()
      .port(8080)
      .host("localhost")
      .addEndpoints(nodesListingServerEndpoint :: docsEndpoints)
      .start()

    println(s"Go to: http://${binding.hostName}:${binding.port}/docs")
    println("Press any key to exit ...")
    scala.io.StdIn.readLine()

    binding.stop()
  }
