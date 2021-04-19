package sttp.tapir.server.play

import play.api.http.HttpEntity
import sttp.tapir.server.interpreter.BodyListener

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PlayBodyListener extends BodyListener[Future, HttpEntity] {
  override def onComplete(body: HttpEntity)(cb: => Future[Unit]): Future[HttpEntity] =
    body match {
      case e @ HttpEntity.Streamed(data, _, _) =>
        Future.successful(e.copy(data = data.watchTermination() { case (_, f) => f.flatMap(_ => cb) }))
      case e @ HttpEntity.Chunked(chunks, _) =>
        Future.successful(e.copy(chunks = chunks.watchTermination() { case (_, f) => f.flatMap(_ => cb) }))
      case e @ HttpEntity.Strict(_, _) => cb.map(_ => e)
    }
}
