package sttp.tapir.examples2.custom_types

// Note that you'll need the extras.auto._ import, not the usual one
import io.circe.generic.extras.auto._
import io.circe.generic.extras.{Configuration => CirceConfiguration}
import sttp.tapir._
import sttp.tapir.generic.Configuration
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerBinding}
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object SealedTraitWithDiscriminator extends App {
  // data structures
  sealed trait Node
  case class Leaf(value: String) extends Node
  case class Branch(name: String, children: Seq[Node]) extends Node

  val discriminatorFieldName = "kind"

  // these configs must match: one configures tapir's schema, the other circe's encoding/decoding to/from json
  implicit val tapirConfig: Configuration = Configuration.default.withDiscriminator(discriminatorFieldName)
  // the CirceConfiguration class is a renamed import (see above), as the name would clash with tapir's Configuration
  implicit val circeConfig: CirceConfiguration = CirceConfiguration.default.withDiscriminator(discriminatorFieldName)

  // endpoint description; the configs are used when deriving the Schema, Encoder and Decoder, which are implicit
  // parameters to jsonBody[Node]
  val nodesListing: PublicEndpoint[Unit, Unit, Node, Any] = endpoint.get
    .in("nodes")
    .out(jsonBody[Node])

  val nodesListingServerEndpoint: ServerEndpoint[Any, Future] =
    nodesListing.serverLogicSuccess[Future](_ => Future.successful(Branch("b", List(Leaf("x"), Leaf("y")))))

  val docsEndpoints = SwaggerInterpreter().fromServerEndpoints[Future](List(nodesListingServerEndpoint), "Nodes", "1.0.0")

  val serverBinding: NettyFutureServerBinding =
    Await.result(
      NettyFutureServer().addEndpoints(nodesListingServerEndpoint :: docsEndpoints).start(),
      Duration.Inf
    )

  println("Go to: http://localhost:8080/docs")
  println("Press any key to exit ...")
  scala.io.StdIn.readLine()

  Await.result(serverBinding.stop(), Duration.Inf)
}
