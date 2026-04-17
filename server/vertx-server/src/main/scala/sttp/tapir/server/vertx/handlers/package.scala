package sttp.tapir.server.vertx

import io.vertx.core.Handler
import io.vertx.ext.web.{Route, RoutingContext}
import io.vertx.ext.web.handler.BodyHandler
import sttp.tapir.{Endpoint, EndpointIO, EndpointOutput}
import sttp.tapir.RawBodyType.MultipartBody
import sttp.tapir.internal._
import sttp.tapir.server.model.MaxContentLength

package object handlers {

  private[vertx] def multipartHandler(uploadDirectory: String, maxBytes: Option[Long]): Handler[RoutingContext] = { rc =>
    rc.request.setExpectMultipart(true)
    maxBytes
      .map(BodyHandler.create(false).setBodyLimit)
      .getOrElse(BodyHandler.create(false))
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

    val maxBytes: Option[Long] =
      e.info
        .attribute(MaxContentLength.attributeKey)
        .map(_.value)

    mbWebsocketType.headOption.orElse(bodyType.headOption) match {
      case Some(MultipartBody(_, _))                          => route.handler(multipartHandler(uploadDirectory, maxBytes))
      case Some(_: EndpointIO.StreamBodyWrapper[_, _])        => route.handler(streamPauseHandler)
      case Some(_: EndpointOutput.WebSocketBodyWrapper[_, _]) => route.handler(streamPauseHandler)
      case Some(_)                                            =>
        route.handler(maxBytes.map(BodyHandler.create(false).setBodyLimit).getOrElse(BodyHandler.create(false)))
      case None => ()
    }

    route
  }
}
