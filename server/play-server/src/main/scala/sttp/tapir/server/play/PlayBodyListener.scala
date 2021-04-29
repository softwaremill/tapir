package sttp.tapir.server.play

import akka.Done
import play.api.http.HttpEntity
import sttp.tapir.server.interpreter.BodyListener

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class PlayBodyListener extends BodyListener[Future, HttpEntity] {
  override def onComplete(body: HttpEntity)(cb: Try[Unit] => Future[Unit]): Future[HttpEntity] = {

    def onDone(f: Future[Done]): Unit = f.onComplete {
      case Failure(ex) => cb(Failure(ex))
      case _           => cb(Success(()))
    }

    body match {
      case e @ HttpEntity.Streamed(data, _, _) =>
        Future.successful(e.copy(data = data.watchTermination() { case (_, f) => onDone(f) }))
      case e @ HttpEntity.Chunked(chunks, _) =>
        Future.successful(e.copy(chunks = chunks.watchTermination() { case (_, f) => onDone(f) }))
      case e @ HttpEntity.Strict(_, _) => cb(Success(())).map(_ => e)
    }
  }
}
