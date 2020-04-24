package sttp.tapir.server.vertx

import io.vertx.scala.ext.web.RoutingContext
import sttp.tapir.Endpoint

import scala.reflect.ClassTag

object VertxInputDecoders {

  def tryEncodeError[E](endpoint: Endpoint[_, E, _, _], rc: RoutingContext, error: Any, ect: Option[ClassTag[E]])
                            (implicit serverOptions: VertxServerOptions): Unit =
    (error, ect) match {
      case (exception: Throwable, Some(ct)) if ct.runtimeClass.isInstance(exception) =>
        encodeError(endpoint, rc, error.asInstanceOf[E])
      case (exception: Throwable, _) =>
        exception.printStackTrace()
        rc.response.setStatusCode(500).end()
      case _ =>
        rc.response.setStatusCode(500).end()
    }

  def encodeError[E](endpoint: Endpoint[_, E, _, _], rc: RoutingContext, error: E): Unit = {
    try {
      VertxOutputEncoders.apply[E](endpoint.errorOutput, error, isError = true)(rc)
    } catch {
      case _: Throwable => rc.response.setStatusCode(500).end()
    }
  }

}
