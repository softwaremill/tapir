package sttp.tapir.server.vertx

import io.vertx.core.Handler
import io.vertx.ext.web.{Route, RoutingContext}
import io.vertx.ext.web.handler.BodyHandler
import sttp.tapir.{Endpoint, EndpointIO}
import sttp.tapir.RawBodyType.MultipartBody
import sttp.tapir.internal._

package object handlers {

  private[vertx] lazy val bodyHandler = BodyHandler.create()

  private[vertx] lazy val multipartHandler: Handler[RoutingContext] = { rc =>
    rc.request.setExpectMultipart(true)
    rc.next()
  }

  private[vertx] lazy val streamPauseHandler: Handler[RoutingContext] = { rc =>
    rc.request.pause()
    rc.next()
  }

  private[vertx] def attachDefaultHandlers[E](e: Endpoint[_, _, E, _, _], route: Route): Route = {
    e.input.asVectorOfBasicInputs() foreach {
      case body: EndpointIO.Body[_, _] =>
        body.bodyType match {
          case MultipartBody(_, _) =>
            route.handler(multipartHandler)
            route.handler(bodyHandler)
          case _ =>
            route.handler(bodyHandler)
        }
      case _: EndpointIO.StreamBodyWrapper[_, _] =>
        route.handler(streamPauseHandler)
      case _ =>
        ()
    }
    route
  }
}
