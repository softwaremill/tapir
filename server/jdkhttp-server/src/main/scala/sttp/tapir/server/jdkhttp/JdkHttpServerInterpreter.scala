package sttp.tapir.server.jdkhttp

import com.sun.net.httpserver.{HttpExchange, HttpHandler}
import sttp.model.{Header, HeaderNames, Headers}
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interpreter.{BodyListener, FilterServerEndpoints, ServerInterpreter}
import sttp.tapir.server.jdkhttp.internal._

import java.io.InputStream
import scala.jdk.CollectionConverters._

trait JdkHttpServerInterpreter {
  def jdkHttpServerOptions: JdkHttpServerOptions

  def toHandler(ses: List[ServerEndpoint[Any, Id]]): HttpHandler = {
    val filteredEndpoints = FilterServerEndpoints[Any, Id](ses)
    val requestBody = new JdkHttpRequestBody(jdkHttpServerOptions.createFile)
    val responseBody = new JdkHttpToResponseBody
    val interceptors = RejectInterceptor.disableWhenSingleEndpoint(jdkHttpServerOptions.interceptors, ses)

    (exchange: HttpExchange) => {
      implicit val bodyListener: BodyListener[Id, InputStream] = new JdkHttpBodyListener(exchange)

      val serverInterpreter = new ServerInterpreter[Any, Id, InputStream, NoStreams](
        filteredEndpoints,
        requestBody,
        responseBody,
        interceptors,
        jdkHttpServerOptions.deleteFile
      )

      val req = JdkHttpServerRequest(exchange)
      println("running serverInterpreter")
      serverInterpreter(req) match {
        case RequestResult.Response(response) =>
          try {
            println(s"got response from interpreter $response")
            exchange.getResponseHeaders.putAll(
              response.headers.groupBy(_.name).view.mapValues(_.map(_.value).asJava).toMap.asJava
            )

            val contentLength = response.headers
              .find {
                case Header(HeaderNames.ContentLength, _) => true
                case _                                    => false
              }
              .map(_.value.toInt)
              .getOrElse(0)

            println(s"content length from response: $contentLength")

            exchange.sendResponseHeaders(response.code.code, contentLength)
            response.body.foreach { is =>
              val os = exchange.getResponseBody
              try {
                is.transferTo(os)
              } finally {
                os.close()
                is.close()
              }
            }
          } finally {
            exchange.close()
          }

        case RequestResult.Failure(t) => // TODO should empty List == 404?
          println(s"got failure from interpreter: $t")
          try {
            if (t.isEmpty) {
              exchange.sendResponseHeaders(404, 0)
            } else {
              exchange.sendResponseHeaders(500, 0)
            }
          } catch {
            case t: Throwable =>
              t.printStackTrace() // TODO drop
              throw t // TODO drop
          } finally {
            exchange.close()
          }
      }
    }
  }
}

object JdkHttpServerInterpreter {
  def apply(serverOptions: JdkHttpServerOptions = JdkHttpServerOptions.Default): JdkHttpServerInterpreter =
    new JdkHttpServerInterpreter {
      override def jdkHttpServerOptions: JdkHttpServerOptions = serverOptions
    }
}
