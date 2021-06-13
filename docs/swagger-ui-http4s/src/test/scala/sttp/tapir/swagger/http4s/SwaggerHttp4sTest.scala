package sttp.tapir.swagger.http4s

import cats.effect.{ContextShift, IO}
import cats.instances.option._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.traverse._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.Location
import org.http4s.syntax.literals._
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext

class SwaggerHttp4sTest extends AnyFlatSpecLike with Matchers with OptionValues {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  val yaml: String = "I love chocolate"

  val contextPath = List("i", "love", "chocolate")
  private val yamlName = "docs.yaml"

  val swaggerDocs = new SwaggerHttp4s(
    yaml = yaml,
    contextPath = List("i", "love", "chocolate"),
    yamlName = yamlName
  )

  it should "redirect to the correct path" in {

    val uri = uri"/i/love/chocolate"
    val expectedLocationHeader = Location(uri.addPath("index.html").withQueryParam("url", s"$uri/$yamlName"))

    val response = swaggerDocs.routes
      .run(Request(GET, uri))
      .value
      .unsafeRunSync()
      .value

    response.status shouldBe Status.PermanentRedirect
    response.headers.toList should contain(expectedLocationHeader)

  }

  it should "return the yaml" in {

    val uri = uri"/i/love/chocolate".addPath(yamlName)

    val (response, body) = swaggerDocs.routes
      .run(Request(GET, uri))
      .value
      .mproduct(_.traverse(_.as[String]))
      .map(_.tupled)
      .unsafeRunSync()
      .value

    response.status shouldBe Status.Ok
    body shouldBe yaml

  }

}
