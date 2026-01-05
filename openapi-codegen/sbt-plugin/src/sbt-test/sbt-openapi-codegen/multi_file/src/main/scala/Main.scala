object Main extends App {
  import sttp.apispec.openapi.circe.yaml._
  import sttp.tapir.generated.swagger.{TapirGeneratedEndpoints => TGE1}
  import sttp.tapir.generated.swagger2.{TapirGeneratedEndpoints => TGE2}
  import sttp.tapir.docs.openapi._

  val docs = OpenAPIDocsInterpreter().toOpenAPI(TGE1.generatedEndpoints ++ TGE2.generatedEndpoints, "My Bookshop", "1.0")

  import java.nio.file.{Paths, Files}
  import java.nio.charset.StandardCharsets

  Files.write(Paths.get("target/swagger.yaml"), docs.toYaml.getBytes(StandardCharsets.UTF_8))
}
