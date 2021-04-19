package sttp.tapir.server.akkahttp

import akka.stream.scaladsl.Flow
import akka.util.ByteString
import sttp.tapir.server.interpreter.BodyListener

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AkkaBodyListener extends BodyListener[Future, AkkaResponseBody] {
  override def onComplete(body: AkkaResponseBody)(cb: => Future[Unit]): AkkaResponseBody =
    body match {
      case ws @ Left(_) =>
        cb
        ws
      case Right(r) => Right(r.transformDataBytes(Flow[ByteString].watchTermination() { case (_, f) => f.flatMap(_ => cb) }))
    }
}
