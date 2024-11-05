// {cat=Schemas; effects=Future; server=Netty; json=circe; docs=Swagger UI}: Customising a derived schema, using annotations, and using implicits

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-swagger-ui-bundle:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.8

package sttp.tapir.examples.schema

import io.circe.generic.auto.*
import sttp.tapir.Schema.annotations.{description, validateEach}
import sttp.tapir.*
import sttp.tapir.generic.Derived
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerBinding}
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

/** @see https://tapir.softwaremill.com/en/v1.0.5/endpoint/schemas.html#schema-derivation */
@main def customisingSchemas(): Unit =
  // schema customised using annotations
  val dragonAgeValidator = Validator.min(100)
  case class Dragon(@description("how people refer to the dragon") name: String, @validateEach(dragonAgeValidator) age: Option[Int])

  // schema customised using implicits
  case class Lizard(name: String, age: Option[Int])
  implicit val customLizardSchema: Schema[Lizard] = implicitly[Derived[Schema[Lizard]]].value
    .modify(_.name)(_.description("how people refer to the lizard"))
    .modify(_.age.each)(_.validate(Validator.max(250)))

  // schema customisation for entire inputs
  val helloDragon = endpoint.get
    .in("hello" / "dragon")
    .in(query[String]("name"))
    .in(query[Option[Int]]("age").validateOption(dragonAgeValidator))
    .out(jsonBody[Dragon])
    .serverLogicPure[Future] { case (name, age) => Right(Dragon(name, age)) }

  // further sample endpoints
  val createDragon = endpoint.post
    .in("hello" / "dragon")
    .in(jsonBody[Dragon])
    .serverLogicPure[Future](_ => Right(()))

  val createLizard = endpoint.post
    .in("hello" / "lizard")
    .in(jsonBody[Lizard])
    .serverLogicPure[Future](_ => Right(()))

  val endpoints = List(helloDragon, createDragon, createLizard)
  val docEndpoints = SwaggerInterpreter().fromServerEndpoints[Future](endpoints, "The tapir ZOO", "1.0.0")

  // start server & test
  val serverBinding: NettyFutureServerBinding =
    Await.result(
      NettyFutureServer().addEndpoints(endpoints ++ docEndpoints).start(),
      Duration.Inf
    )

  println("Go to: http://localhost:8080/docs")
  println("Press any key to exit ...")
  scala.io.StdIn.readLine()

  Await.result(serverBinding.stop(), Duration.Inf)
