package sttp.tapir.serverless.aws.sam

import cats.data.NonEmptySeq
import io.circe.generic.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.model.Method
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.serverless.aws.sam.VerifySamTemplateTest._

import scala.concurrent.duration._
import scala.io.Source

class VerifySamTemplateTest extends AnyFunSuite with Matchers {

  test("should match the expected yaml with image source") {
    val expectedYaml = load("image_source_template.yaml")

    val samOptions: AwsSamOptions = AwsSamOptions(
      "PetApi",
      source = ImageSource("image.repository:pet-api"),
      timeout = 10.seconds,
      memorySize = 1024
    )

    val actualYaml = AwsSamInterpreter(samOptions).toSamTemplate(List(getPetEndpoint, addPetEndpoint, getCutePetsEndpoint)).toYaml

    expectedYaml shouldBe noIndentation(actualYaml)
  }

  test("should match the expected yaml with code source") {
    val expectedYaml = load("code_source_template.yaml")

    val samOptions: AwsSamOptions = AwsSamOptions(
      "PetApi",
      source = CodeSource(runtime = "java11", codeUri = "/somewhere/pet-api.jar", "pet.api.Handler::handleRequest"),
      timeout = 10.seconds,
      memorySize = 1024
    )

    val actualYaml = AwsSamInterpreter(samOptions).toSamTemplate(List(getPetEndpoint, addPetEndpoint, getCutePetsEndpoint)).toYaml

    expectedYaml shouldBe noIndentation(actualYaml)
  }

  test("should match the expected yaml with params, code source and role") {
    val expectedYaml = load("code_source_template_with_params_and_role.yaml")

    val samOptions: AwsSamOptions = AwsSamOptions(
      "PetApi",
      source = CodeSource(runtime = "java11", codeUri = "/somewhere/pet-api.jar", "pet.api.Handler::handleRequest"),
      timeout = 10.seconds,
      memorySize = 1024
    )
      .withParameter("PetApiJava", None)
      .withParameter("PetApiLambdaAuthorizer", Some("Lambda authorizer for authentication"))
      .withParameter("PetApiLambdaAuthorizerRoleArn", Some("Role for lambda authorizer"))
      .withParameter("PetApiLambdaRoleArn", Some("Lambda role for basic execution and permissions"))
      .customiseOptions { case ((jvm, authArn, authRole, role), originalOptions) =>
        val functionSource = originalOptions.source.asInstanceOf[CodeSource].copy(runtime = jvm.ref, role = Some(role.ref))
        val lambdaAuth = HttpApiProperties.Auth.Lambda(
          name = "MyLambdaAuthorizer",
          version = HttpApiProperties.Auth.Version.V2Simple,
          enableDefaultPermissions = true,
          functionArn = authArn.ref,
          functionRole = Some(authRole.ref),
          identity = HttpApiProperties.Auth
            .LambdaAuthorizationIdentitySource(
              headers = Some(NonEmptySeq.one("Authorization")),
              queryStrings = None,
              reauthorizeEvery = None
            )
        )
        val httpApiProperties = originalOptions.httpApi
          .getOrElse(HttpApiProperties(None, None))
          .copy(auths = Some(HttpApiProperties.Auths(NonEmptySeq.one(lambdaAuth), Some(lambdaAuth.name))))
        originalOptions.copy(source = functionSource, httpApi = Some(httpApiProperties))
      }

    val actualYaml = AwsSamInterpreter(samOptions).toSamTemplate(List(getPetEndpoint, addPetEndpoint, getCutePetsEndpoint)).toYaml

    expectedYaml shouldBe noIndentation(actualYaml)
  }

  test("should match the expected yaml with HttpApi properties") {
    val expectedYaml = load("http_api_template.yaml")

    val samOptions: AwsSamOptions = AwsSamOptions(
      "PetApi",
      httpApi = Some(
        HttpApiProperties(cors =
          Some(
            HttpApiProperties.Cors(
              allowCredentials = Some(HttpApiProperties.AllowedCredentials.Deny),
              allowedHeaders = Some(HttpApiProperties.AllowedHeaders.All),
              allowedMethods = Some(HttpApiProperties.AllowedMethods.Some(Set(Method.GET, Method.POST))),
              allowedOrigins = Some(HttpApiProperties.AllowedOrigin.All),
              exposeHeaders = None,
              maxAge = Some(HttpApiProperties.MaxAge.Some(0.seconds))
            )
          )
        )
      ),
      source = CodeSource(
        runtime = "java11",
        codeUri = "/somewhere/pet-api.jar",
        "pet.api.Handler::handleRequest",
        environment = Map("myEnv" -> "foo", "otherEnv" -> "bar")
      ),
      timeout = 10.seconds,
      memorySize = 1024
    )

    val actualYaml = AwsSamInterpreter(samOptions).toSamTemplate(List(getPetEndpoint, addPetEndpoint, getCutePetsEndpoint)).toYaml

    expectedYaml shouldBe noIndentation(actualYaml)
  }

}

object VerifySamTemplateTest {

  case class Pet(name: String, species: String)

  val getPetEndpoint: PublicEndpoint[Int, Unit, Pet, Any] = endpoint.get
    .in("api" / "pets" / path[Int]("id"))
    .out(jsonBody[Pet])

  val addPetEndpoint: PublicEndpoint[Pet, Unit, Unit, Any] = endpoint.post
    .in("api" / "pets")
    .in(jsonBody[Pet])

  val getCutePetsEndpoint: PublicEndpoint[Unit, Unit, Pet, Any] = endpoint.get
    .in("api" / "cute-pets")
    .out(jsonBody[Pet])

  def load(fileName: String): String = {
    noIndentation(Source.fromInputStream(getClass.getResourceAsStream(s"/$fileName")).getLines().mkString("\n"))
  }

  def noIndentation(s: String): String = s.replaceAll("[ \t]", "").trim
}
