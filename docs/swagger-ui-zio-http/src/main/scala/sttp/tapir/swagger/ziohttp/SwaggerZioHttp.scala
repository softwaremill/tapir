package sttp.tapir.swagger.ziohttp

import zhttp.http._
import zio.Chunk
import zio.blocking.Blocking
import zio.stream.ZStream

import java.util.Properties

class SwaggerZioHttp(
    yaml: String,
    contextPath: String = "docs",
    yamlName: String = "docs.yaml"
) {
  private val resourcePathPrefix = {
    val swaggerVersion: String = {
      val p = new Properties()
      val pomProperties = getClass.getResourceAsStream("/META-INF/maven/org.webjars/swagger-ui/pom.properties")
      try p.load(pomProperties)
      finally pomProperties.close()
      p.getProperty("version")
    }
    s"META-INF/resources/webjars/swagger-ui/$swaggerVersion"
  }

  def route: Http[Blocking, Throwable, Request, Response[Blocking, Throwable]] = {
    Http.collect[Request] {
      case Method.GET -> Root / `contextPath` =>
        val location = s"/$contextPath/index.html?url=/$contextPath/$yamlName"
        Response.http(Status.MOVED_PERMANENTLY, List(Header.custom("Location", location)))
      case Method.GET -> Root / `contextPath` / `yamlName` =>
        val body = HttpData.CompleteData(Chunk.fromArray(yaml.getBytes(HTTP_CHARSET)))
        Response.http[Blocking, Throwable](Status.OK, List(Header.custom("content-type", "text/yaml")), body)
      case Method.GET -> Root / `contextPath` / swaggerResource =>
        val staticResource = this.getClass.getClassLoader.getResourceAsStream(s"$resourcePathPrefix/$swaggerResource")
        val content = HttpData.fromStream(ZStream.fromInputStream(staticResource))
        Response.http(content = content)
    }
  }
}
