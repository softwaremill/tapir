package sttp.tapir.swagger.ziohttp

import java.util.Properties

class SwaggerZioHttp(
    yaml: String,
    contextPath: List[String] = List("docs"),
    yamlName: String = "docs.yaml"
) {
  private val swaggerVersion: String = {
    val p = new Properties()
    val pomProperties = getClass.getResourceAsStream("/META-INF/maven/org.webjars/swagger-ui/pom.properties")
    try p.load(pomProperties)
    finally pomProperties.close()
    p.getProperty("version")
  }

}
