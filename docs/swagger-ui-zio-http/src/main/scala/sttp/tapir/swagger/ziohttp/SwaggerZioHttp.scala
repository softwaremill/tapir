package sttp.tapir.swagger.ziohttp

import zhttp.http._
import zio.Chunk
import zio.blocking.Blocking
import zio.stream.ZStream

import java.nio.file.Paths
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
      case Method.GET -> Root / path =>
        if (path.equals(contextPath)) {
          val location = s"/$contextPath/index.html?url=/$contextPath/$yamlName"
          Response.http(Status.MOVED_PERMANENTLY, List(Header.custom("Location", location)))
        } else Response.http(Status.NOT_FOUND)
      case Method.GET -> Root / path / yamlName =>
        if (path.equals(contextPath)) {
          if (yamlName.equals(yamlName)) {
            val body = HttpData.CompleteData(Chunk.fromArray(yaml.getBytes(HTTP_CHARSET)))
            Response.http[Blocking, Throwable](Status.OK, List(Header.custom("content-type", "text/yaml")), body)
          } else {
            val content = HttpData.fromStream(ZStream.fromFile(Paths.get(s"$resourcePathPrefix/$yamlName")))
            Response.http(content = content)
          }
        } else Response.http(Status.NOT_FOUND)
    }
  }
}
