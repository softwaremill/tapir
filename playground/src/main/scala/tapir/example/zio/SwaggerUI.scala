package tapir.example.zio

import cats.effect.{ContextShift, Sync}
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location

import scala.concurrent.ExecutionContext

class SwaggerUI[F[_]: ContextShift: Sync](yaml: String) {
  import java.util.Properties

  private val DocsContext = "docs"
  private val DocsYaml = "docs.yml"

  private val swaggerVersion = {
    val p = new Properties()
    val pomProperties = getClass.getResourceAsStream("/META-INF/maven/org.webjars/swagger-ui/pom.properties")
    try p.load(pomProperties)
    finally pomProperties.close()
    p.getProperty("version")
  }

  def routes: HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    HttpRoutes.of[F] {
      case GET -> Root =>
        PermanentRedirect(Location(Uri.fromString(s"/$DocsContext/index.html?url=/$DocsContext/$DocsYaml").right.get))
      case GET -> Root / DocsYaml =>
        Ok(yaml)
      case r =>
        StaticFile
          .fromResource(s"/META-INF/resources/webjars/swagger-ui/$swaggerVersion${r.pathInfo}", ExecutionContext.global)
          .getOrElseF(NotFound())
    }
  }
}
