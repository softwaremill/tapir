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
    val bodyType = e.asVectorOfBasicInputs().flatMap {
      case body: EndpointIO.Body[_, _]              => Vector(body.bodyType)
      case EndpointIO.OneOfBody(variants, _)        => variants.map(_.body.bodyType).toVector
      case body: EndpointIO.StreamBodyWrapper[_, _] => Vector(body)
      case _                                        => Vector.empty
    }

    bodyType.headOption match {
      case Some(MultipartBody(_, _)) =>
        route.handler(multipartHandler)
        route.handler(bodyHandler)
      case Some(_: EndpointIO.StreamBodyWrapper[_, _]) => route.handler(streamPauseHandler)
      case Some(_)                                     => route.handler(bodyHandler)
      case None                                        => ()
    }

    route
  }
}
