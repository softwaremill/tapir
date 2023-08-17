package sttp.tapir.server.vertx

import io.vertx.core.Handler
import io.vertx.ext.web.{Route, RoutingContext}
import io.vertx.ext.web.handler.BodyHandler
import sttp.tapir.{Endpoint, EndpointIO, EndpointOutput}
import sttp.tapir.RawBodyType.MultipartBody
import sttp.tapir.internal._

package object handlers {

  private[vertx] lazy val bodyHandler = BodyHandler.create(false)

  private[vertx] def multipartHandler(uploadDirectory: String): Handler[RoutingContext] = { rc =>
    rc.request.setExpectMultipart(true)
    bodyHandler
      .setHandleFileUploads(true)
      .setUploadsDirectory(uploadDirectory)
      .handle(rc)
  }

  private[vertx] lazy val streamPauseHandler: Handler[RoutingContext] = { rc =>
    rc.request.pause()
    rc.next()
  }

  private[vertx] def attachDefaultHandlers[E](e: Endpoint[_, _, E, _, _], route: Route, uploadDirectory: String): Route = {
    val mbWebsocketType = e.output.traverseOutputs[EndpointOutput.WebSocketBodyWrapper[_, _]] {
      case body: EndpointOutput.WebSocketBodyWrapper[_, _] => Vector(body)
    }

    val bodyType = e.asVectorOfBasicInputs().flatMap {
      case body: EndpointIO.Body[_, _]              => Vector(body.bodyType)
      case EndpointIO.OneOfBody(variants, _)        => variants.flatMap(_.body.fold(body => Some(body.bodyType), _.bodyType)).toVector
      case body: EndpointIO.StreamBodyWrapper[_, _] => Vector(body)
      case _                                        => Vector.empty
    }

    mbWebsocketType.headOption.orElse(bodyType.headOption) match {
      case Some(MultipartBody(_, _))                          => route.handler(multipartHandler(uploadDirectory))
      case Some(_: EndpointIO.StreamBodyWrapper[_, _])        => route.handler(streamPauseHandler)
      case Some(_: EndpointOutput.WebSocketBodyWrapper[_, _]) => route.handler(streamPauseHandler)
      case Some(_)                                            => route.handler(bodyHandler)
      case None                                               => ()
    }

    route
  }
}
