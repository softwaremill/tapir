package sttp.tapir.server.vertx

import io.vertx.core.Handler
import io.vertx.scala.ext.web.{Route, RoutingContext}
import io.vertx.scala.ext.web.handler.BodyHandler
import sttp.tapir.{Endpoint, EndpointIO}
import sttp.tapir.internal._
import sttp.tapir.RawBodyType.MultipartBody
import sttp.tapir.server.vertx.encoders.VertxOutputEncoders

import scala.reflect.ClassTag

package object handlers {

  private[vertx] lazy val bodyHandler = BodyHandler.create()

  private[vertx] lazy val streamPauseHandler: Handler[RoutingContext] = { rc =>
    rc.request.pause()
    rc.next()
  }

  private[vertx] lazy val multipartHandler: Handler[RoutingContext] = { rc =>
    rc.request.setExpectMultipart(true)
    rc.next()
  }

  private[vertx] def bodyHandlers(body: EndpointIO.Body[_, _]) =
    body.bodyType match {
      case MultipartBody(_, _) => List(multipartHandler, bodyHandler)
      case _                   => List(bodyHandler)
    }

  private[vertx] def attachDefaultHandlers[E](
      e: Endpoint[_, E, _, _],
      route: Route
  )(implicit serverOptions: VertxEndpointOptions, ect: Option[ClassTag[E]]): Route = {
    route.failureHandler(rc => tryEncodeError(e, rc, rc.failure))
    val inputs = e.input.asVectorOfBasicInputs()
    inputs
      .foldLeft(List[Handler[RoutingContext]]()) { (handlers, ep) =>
        ep match {
          case body: EndpointIO.Body[_, _]              => bodyHandlers(body) ++ handlers
          case _: EndpointIO.StreamBodyWrapper[_, _, _] => streamPauseHandler :: handlers
          case _                                        => handlers
        }
      }
      .foreach(route.handler)
    route
  }

  /** Encodes an error given an endpoint definition, by trying to invoke the endpoint.errorOut, or just failing properly
    * @param endpoint the endpoint definition
    * @param rc the RoutingContext
    * @param error the error to write to the response
    * @param endpointOptions the endpoint options
    * @param ect an eventual ClassTag for user-defined exceptions
    * @tparam E the type of the error
    */
  private[vertx] def tryEncodeError[E](endpoint: Endpoint[_, E, _, _], rc: RoutingContext, error: Any)(implicit
      endpointOptions: VertxEndpointOptions,
      ect: Option[ClassTag[E]]
  ): Unit =
    (error, ect) match {
      case (exception: Throwable, Some(ct)) if ct.runtimeClass.isInstance(exception) =>
        encodeError(endpoint, rc, error.asInstanceOf[E])
      case _ =>
        rc.response.setStatusCode(500).end()
    }

  private[vertx] def encodeError[E](endpoint: Endpoint[_, E, _, _], rc: RoutingContext, error: E)(implicit
      endpointOptions: VertxEndpointOptions
  ): Unit = {
    try {
      VertxOutputEncoders.apply[E](endpoint.errorOutput, error, isError = true)(endpointOptions)(rc)
    } catch {
      case _: Throwable => rc.response.setStatusCode(500).end()
    }
  }

}
