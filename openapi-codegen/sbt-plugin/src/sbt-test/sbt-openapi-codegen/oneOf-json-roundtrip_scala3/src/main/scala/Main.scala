object Main extends App {
  import sttp.apispec.openapi.circe.yaml._
  import sttp.tapir.generated._
  import sttp.tapir.docs.openapi._

  val docs = OpenAPIDocsInterpreter().toOpenAPI(TapirGeneratedEndpoints.generatedEndpoints, "My Bookshop", "1.0")

  import java.nio.file.{Paths, Files}
  import java.nio.charset.StandardCharsets

  private val outputYaml: String = docs.toYaml
  Files.write(Paths.get("target/swagger.yaml"), outputYaml.getBytes(StandardCharsets.UTF_8))
  assert(
    outputYaml.contains("""    AnEnum:
                          |      title: AnEnum
                          |      type: string
                          |      enum:
                          |      - Bar
                          |      - Baz
                          |      - Foo""".stripMargin),
    "enums look wrong :'("
  )
}
