package sttp.tapir.swagger.play

import java.util.Properties

import com.typesafe.config.ConfigFactory
import play.api.http.{DefaultFileMimeTypes, FileMimeTypes, FileMimeTypesConfiguration, MimeTypes}
import play.api.mvc.{ActionBuilder, AnyContent, Request}
import play.api.mvc.Results._
import play.api.routing.Router.Routes
import play.api.routing.sird._

import scala.concurrent.ExecutionContext

/** Usage: add `new SwaggerPlay(yaml).routes` to your Play routes. Docs will be available using the `/docs` path.
  * To re-use the ActionBuilder from an existing PlayServerOptions instance, import `sttp.tapir.server.play._`
  *
  * @param yaml           The yaml with the OpenAPI documentation.
  * @param contextPath    The context in which the documentation will be served. Defaults to `docs`, so the address
  *                       of the docs will be `/docs`.
  * @param yamlName       The name of the file, through which the yaml documentation will be served. Defaults to `docs.yaml`.
  */
class SwaggerPlay(
    yaml: String,
    contextPath: String = "docs",
    yamlName: String = "docs.yaml"
)(implicit
    ec: ExecutionContext,
    actionBuilder: ActionBuilder[Request, AnyContent]
) {
  private val redirectPath = s"/$contextPath/index.html?url=/$contextPath/$yamlName"
  private val resourcePathPrefix = {
    val swaggerVersion: String = {
      ConfigFactory.load()
      val p = new Properties()
      val pomProperties = getClass.getResourceAsStream("/META-INF/maven/org.webjars/swagger-ui/pom.properties")
      try p.load(pomProperties)
      finally pomProperties.close()
      p.getProperty("version")
    }
    s"META-INF/resources/webjars/swagger-ui/$swaggerVersion"
  }

  private implicit val swaggerUIFileMimeTypes: FileMimeTypes = new DefaultFileMimeTypes(
    FileMimeTypesConfiguration(
      Map(
        "html" -> MimeTypes.HTML,
        "css" -> MimeTypes.CSS,
        "js" -> MimeTypes.JAVASCRIPT,
        "png" -> "image/png"
      )
    )
  )

  def routes: Routes = {
    case GET(p"/$path*") if path.startsWith(contextPath) =>
      val filePart = path.substring(contextPath.length)
      // Remove the first '/' if present
      val file = if (filePart.nonEmpty) filePart.substring(1) else filePart
      if (file.isEmpty) {
        actionBuilder {
          MovedPermanently(redirectPath)
        }
      } else {
        actionBuilder {
          file match {
            case `yamlName` =>
              Ok(yaml).as("text/yaml")
            case _ =>
              Ok.sendResource(s"$resourcePathPrefix/$file")
          }
        }
      }
  }
}
