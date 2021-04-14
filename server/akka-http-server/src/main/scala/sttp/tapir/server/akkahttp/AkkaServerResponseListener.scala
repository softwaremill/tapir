package sttp.tapir.server.akkahttp

import akka.stream.scaladsl.Flow
import akka.util.ByteString
import sttp.tapir.model.ServerResponse
import sttp.tapir.server.interpreter.ServerResponseListener

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AkkaServerResponseListener extends ServerResponseListener[Future, AkkaResponseBody] {
  override def listen(r: ServerResponse[AkkaResponseBody])(cb: => Future[Unit]): ServerResponse[AkkaResponseBody] = {
    r.copy(body = r.body.map {
      case Left(ws) => Left(ws.watchTermination() { case (_, f) => f.flatMap(_ => cb) })
      case Right(r) => Right(r.transformDataBytes(Flow[ByteString].watchTermination() { case (_, f) => f.flatMap(_ => cb) }))
    })
  }
}
