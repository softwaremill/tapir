package sttp.tapir.swagger.vertx

import java.util.Properties

import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.{Route, Router}
import io.vertx.ext.web.handler.StaticHandler

class SwaggerVertx(yaml: String, contextPath: String = "docs", yamlName: String = "docs.yaml") {

  private val staticHandler = {
    val p = new Properties()
    val pomProperties = getClass.getResourceAsStream("/META-INF/maven/org.webjars/swagger-ui/pom.properties")
    try p.load(pomProperties)
    finally pomProperties.close()
    val swaggerVersion = p.getProperty("version")
    StaticHandler
      .create(s"META-INF/resources/webjars/swagger-ui/$swaggerVersion/")
      .setCachingEnabled(false)
  }

  def route(router: Router): Route = {
    router.route(HttpMethod.GET, s"/$contextPath")
      .handler { ctx =>
        ctx.redirect(s"/$contextPath/index.html?url=/$contextPath/$yamlName")
        ()
      }

    router.route(HttpMethod.GET, s"/$contextPath/$yamlName")
      .handler { ctx =>
        ctx.response().putHeader("Content-Type", "application/yaml").end(yaml)
        ()
      }

    router.route(s"/$contextPath/*").handler(staticHandler)
  }
}
