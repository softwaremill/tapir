package sttp.tapir.server.jdkhttp

import com.sun.net.httpserver.{HttpExchange, HttpHandler}
import sttp.model.{Header, HeaderNames}
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interpreter.{BodyListener, FilterServerEndpoints, ServerInterpreter}
import sttp.tapir.server.jdkhttp.internal._

import scala.jdk.CollectionConverters._

trait JdkHttpServerInterpreter {
  def jdkHttpServerOptions: JdkHttpServerOptions

  def toHandler(ses: List[ServerEndpoint[Any, Id]]): HttpHandler = {
    val filteredEndpoints = FilterServerEndpoints[Any, Id](ses)
    val requestBody = new JdkHttpRequestBody(jdkHttpServerOptions.createFile, jdkHttpServerOptions.multipartFileThresholdBytes)
    val responseBody = new JdkHttpToResponseBody
    val interceptors = RejectInterceptor.disableWhenSingleEndpoint(jdkHttpServerOptions.interceptors, ses)

    implicit val bodyListener: BodyListener[Id, JdkHttpResponseBody] = new JdkHttpBodyListener

    val serverInterpreter = new ServerInterpreter[Any, Id, JdkHttpResponseBody, NoStreams](
      filteredEndpoints,
      requestBody,
      responseBody,
      interceptors,
      jdkHttpServerOptions.deleteFile
    )

    (exchange: HttpExchange) => {
      // the exchange objects seem to be reused, hence always resetting the handled flag
      JdkHttpServerInterpreter.setRequestHandled(exchange, v = false)

      val req = JdkHttpServerRequest(exchange)
      serverInterpreter(req) match {
        case RequestResult.Response(response) =>
          try {
            exchange.getResponseHeaders.putAll(
              response.headers.groupBy(_.name).view.mapValues(_.map(_.value).asJava).toMap.asJava
            )

            val contentLengthFromHeader = response.headers
              .find {
                case Header(HeaderNames.ContentLength, _) => true
                case _                                    => false
              }
              .map(_.value.toLong)
              .getOrElse(0L)

            response.body match {
              case Some((is, maybeContentLength)) =>
                val contentLength = maybeContentLength.getOrElse(contentLengthFromHeader)
                exchange.sendResponseHeaders(response.code.code, contentLength)
                val os = exchange.getResponseBody
                try is.transferTo(os)
                finally {
                  is.close()
                  os.close()
                }
              case None =>
                exchange.sendResponseHeaders(response.code.code, contentLengthFromHeader)
            }
          } finally {
            JdkHttpServerInterpreter.setRequestHandled(exchange, v = true)
            exchange.close()
          }

        case RequestResult.Failure(_) =>
          if (jdkHttpServerOptions.send404WhenRequestNotHandled) {
            JdkHttpServerInterpreter.setRequestHandled(exchange, v = true)
            try exchange.sendResponseHeaders(404, -1)
            finally exchange.close()
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

  private val handledAttribute = "TAPIR_REQUEST_HANDLED"
  private[jdkhttp] def setRequestHandled(exchange: HttpExchange, v: Boolean): Unit = exchange.setAttribute(handledAttribute, v)
  def isRequestHandled(exchange: HttpExchange): Boolean = exchange.getAttribute(handledAttribute) == true
}
