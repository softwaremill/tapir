package sttp.tapir.redoc.play

import play.api.mvc.{ActionBuilder, AnyContent, Request}
import play.api.mvc.Results._
import play.api.routing.Router.Routes
import play.api.routing.sird._

/** Usage: add `new RedocPlay("My App", yaml).routes` to your Play routes. Docs will be available using the `/docs` path.
  * To re-use the ActionBuilder from an existing PlayServerOptions instance, import `sttp.tapir.server.play._`
  *
  * @param title          The title of the HTML page.
  * @param yaml           The yaml with the OpenAPI documentation.
  * @param contextPath    The path of the redoc.html page.
  * @param yamlName       The name of the file, through which the yaml documentation will be served. Defaults to `docs.yaml`.
  * @param redocVersion   The version of redoc to use. Defaults to `next`. Visit https://github.com/Redocly/redoc for more information.
  */
class RedocPlay(
    title: String,
    yaml: String,
    contextPath: String = "doc",
    yamlName: String = "docs.yaml",
    redocVersion: String = "next"
)(implicit actionBuilder: ActionBuilder[Request, AnyContent]) {
  def routes: Routes = {
    case GET(p"/$path") if path == contextPath =>
      actionBuilder {
        PermanentRedirect(s"/$contextPath/redoc.html")
      }
    case GET(p"/$path/$file") if path == contextPath =>
      actionBuilder {
        file match {
          case "redoc.html" =>
            Ok(html.redoc(title, s"/$contextPath/$yamlName", redocVersion))
          case `yamlName` =>
            Ok(yaml).as("text/yaml")
          case f => NotFound(f)
        }

      }
  }
}
