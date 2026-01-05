object Main extends App {
  import sttp.apispec.openapi.circe.yaml._
  import sttp.tapir.generated._
  import sttp.tapir.docs.openapi._

  val docs =
    OpenAPIDocsInterpreter().toOpenAPI(TapirGeneratedEndpoints.generatedEndpoints, "Swagger Petstore - OpenAPI 3.0", "1.0.27-SNAPSHOT")

  import java.nio.file.{Paths, Files}
  import java.nio.charset.StandardCharsets

  Files.write(Paths.get("target/swagger.yaml"), docs.toYaml.getBytes(StandardCharsets.UTF_8))
}
