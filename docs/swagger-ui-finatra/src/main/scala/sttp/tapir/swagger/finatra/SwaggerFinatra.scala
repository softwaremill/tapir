package sttp.tapir.swagger.finatra

import java.io.InputStream
import java.util.Properties

import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.io.{Buf, InputStreamReader, Reader}
import com.twitter.util.Future

/**
  * Usage: add `new SwaggerFinatra(yaml)` to your Finatra HttpServer's configureHttp function. For example:
  *
  * <pre>
  * override def configureHttp(router: HttpRouter): Unit =
  *  router
  *    .filter[LoggingMDCFilter[Request, Response]]
  *    .filter[TraceIdMDCFilter[Request, Response]]
  *    .filter[CommonFilters]
  *    .add(new SwaggerFinatra(yaml))
  * </pre>
  *
  * When using a custom `contextPath` is used, replace `/docs` with that value.
  *
  * @param yaml        The yaml with the OpenAPI documentation.
  * @param contextPath The context in which the documentation will be served. Defaults to `docs`, so the address
  *                    of the docs will be `/docs`.
  * @param yamlName    The name of the file, through which the yaml documentation will be served. Defaults to `docs.yaml`.
  */
class SwaggerFinatra(yaml: String, contextPath: String = "docs", yamlName: String = "docs.yaml") extends Controller {
  private val swaggerVersion: String = {
    val p = new Properties()
    val pomProperties = getClass.getResourceAsStream("/META-INF/maven/org.webjars/swagger-ui/pom.properties")
    try p.load(pomProperties)
    finally pomProperties.close()
    p.getProperty("version")
  }

  get(route = s"/$contextPath/?") { _: Request =>
    response.movedPermanently.location(s"/$contextPath/index.html?url=/$contextPath/$yamlName")
  }

  get(route = s"/$contextPath/$yamlName") { _: Request =>
    response.ok(yaml).contentType("application/yaml")
  }

  get(route = s"/$contextPath/:swaggerResource") { req: Request =>
    val sResource: String = req.getParam("swaggerResource")
    val sIStreamOpt: Option[InputStream] =
      Option(getClass.getResourceAsStream(s"/META-INF/resources/webjars/swagger-ui/$swaggerVersion/$sResource"))

    sIStreamOpt.fold(Future.value(response.notFound))(is =>
      Reader
        .readAllItems(
          InputStreamReader(is)
        )
        .map { bs =>
          val bytes: Array[Byte] = Buf.ByteArray.Shared.extract(bs.fold(Buf.Empty)(_.concat(_)))

          if (sResource.endsWith(".html")) {
            response.ok.html(new String(bytes, "UTF-8"))
          } else if (sResource.endsWith(".css")) {
            response.ok(new String(bytes, "UTF-8")).contentType("text/css")
          } else if (sResource.endsWith(".js")) {
            response.ok(new String(bytes, "UTF-8")).contentType("text/javascript")
          } else {
            response.ok(bytes).contentType("image/png")
          }
        }
    )
  }
}
