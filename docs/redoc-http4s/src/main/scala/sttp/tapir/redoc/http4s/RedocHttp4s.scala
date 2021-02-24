package sttp.tapir.redoc.http4s

import cats.effect.{ContextShift, Sync}
import org.http4s.dsl.Http4sDsl
import org.http4s.headers._
import org.http4s.{Charset, HttpRoutes, MediaType}

import scala.io.Source

/** Usage: add `new RedocHttp4s(title, yaml).routes[F]` to your http4s router. For example:
  * `Router("/docs" -> new RedocHttp4s(yaml).routes[IO])`.
  *
  * @param title        The title of the HTML page.
  * @param yaml         The yaml with the OpenAPI documentation.
  * @param yamlName     The name of the file, through which the yaml documentation will be served. Defaults to `docs.yaml`.
  * @param redocVersion The semver version of Redoc to use. Defaults to `2.0.0-rc.23`.
  * @param contextPath  The base path of the documentation
  */
class RedocHttp4s(
                       title: String,
                       yaml: String,
                       yamlName: String = "docs.yaml",
                       redocVersion: String = "2.0.0-rc.23",
                       contextPath: String = "",
                     ) {

  private lazy val html = {
    val fileName = "redoc.html"
    val is = getClass.getClassLoader.getResourceAsStream(fileName)
    assert(Option(is).nonEmpty, s"Could not find file ${fileName} on classpath.")
    val rawHtml = Source.fromInputStream(is).mkString

    rawHtml.replace("{{docsPath}}", yamlName).replace("{{title}}", title).replace("{{redocVersion}}", redocVersion)
  }

  def routes[F[_]: ContextShift: Sync]: HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    contextPath match {
      case "" =>
        HttpRoutes.of[F] {
          case GET -> Root =>
            Ok(html, `Content-Type`(MediaType.text.html, Charset.`UTF-8`))
          case GET -> Root / `yamlName` =>
            Ok(yaml, `Content-Type`(MediaType.text.yaml, Charset.`UTF-8`))
        }
      case _ =>
        HttpRoutes.of[F] {
          case GET -> Root / `contextPath` / "" =>
            Ok(html, `Content-Type`(MediaType.text.html, Charset.`UTF-8`))
          case req@GET -> Root / `contextPath` =>
            PermanentRedirect(Location(req.uri.withPath(req.uri.path.concat("/"))))
          case GET -> Root / `contextPath` / `yamlName` =>
            Ok(yaml, `Content-Type`(MediaType.text.yaml, Charset.`UTF-8`))
        }
    }
  }
}
