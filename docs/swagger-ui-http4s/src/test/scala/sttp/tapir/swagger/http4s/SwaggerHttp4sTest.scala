package sttp.tapir.swagger.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.instances.option._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.traverse._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.syntax.literals._
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.typelevel.ci.CIString

class SwaggerHttp4sTest extends AnyFlatSpecLike with Matchers with OptionValues {

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
    val expectedLocationHeader = uri.addPath("index.html").withQueryParam("url", s"$uri/$yamlName")

    val response = swaggerDocs
      .routes[IO]
      .run(Request(GET, uri))
      .value
      .unsafeRunSync()
      .value

    response.status shouldBe Status.PermanentRedirect
    response.headers.headers.find(_.name == CIString("Location")).map(_.value) shouldBe Some(expectedLocationHeader.toString)
  }

  it should "return the yaml" in {
    val uri = uri"/i/love/chocolate".addPath(yamlName)

    val (response, body) = swaggerDocs
      .routes[IO]
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
