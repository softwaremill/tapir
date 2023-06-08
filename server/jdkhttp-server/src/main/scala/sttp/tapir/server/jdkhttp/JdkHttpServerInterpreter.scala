package sttp.tapir.server.jdkhttp

import com.sun.net.httpserver.{HttpExchange, HttpHandler}
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
            exchange.sendResponseHeaders(response.code.code, 0)
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

        case RequestResult.Failure(t) =>
          println(s"got failure from interpreter: $t")
          try { // TODO drop
            exchange.sendResponseHeaders(500, 0)
            exchange.close()
          } catch { // TODO drop
            case t: Throwable => t.printStackTrace()
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
