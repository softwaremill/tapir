package sttp.tapir.serverless.aws.sam

import io.circe.generic.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.serverless.aws.sam.VerifySamTemplateTest._
import sttp.tapir.{Endpoint, endpoint, path, _}

import scala.io.Source

class VerifySamTemplateTest extends AnyFunSuite with Matchers {

  test("should match the expected yaml with image source") {
    val expectedYaml = load("image_source_template.yaml")

    implicit val samOptions: AwsSamOptions = AwsSamOptions(
      "PetApi",
      source = ImageSource("image.repository:pet-api"),
      memorySize = 1024
    )

    val actualYaml = AwsSamInterpreter.toSamTemplate(List(getPetEndpoint, addPetEndpoint)).toYaml

    println(actualYaml)

    expectedYaml shouldBe noIndentation(actualYaml)
  }

  test("should match the expected yaml with code source") {
    val expectedYaml = load("code_source_template.yaml")

    implicit val samOptions: AwsSamOptions = AwsSamOptions(
      "PetApi",
      source = CodeSource(runtime = "java11", codeUri = "/somewhere/pet-api.jar", "pet.api.Handler::handleRequest"),
      memorySize = 1024
    )

    val actualYaml = AwsSamInterpreter.toSamTemplate(List(getPetEndpoint, addPetEndpoint)).toYaml

    println(actualYaml)

    expectedYaml shouldBe noIndentation(actualYaml)
  }

}

object VerifySamTemplateTest {

  case class Pet(name: String, species: String)

  val getPetEndpoint: Endpoint[Int, Unit, Pet, Any] = endpoint.get
    .in("api" / "pets" / path[Int]("id"))
    .out(jsonBody[Pet])

  val addPetEndpoint: Endpoint[Pet, Unit, Unit, Any] = endpoint.post
    .in("api" / "pets")
    .in(jsonBody[Pet])

  def load(fileName: String): String = {
    noIndentation(Source.fromInputStream(getClass.getResourceAsStream(s"/$fileName")).getLines().mkString("\n"))
  }

  def noIndentation(s: String): String = s.replaceAll("[ \t]", "").trim
}
