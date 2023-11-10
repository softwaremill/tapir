package sttp.tapir.server.play

import akka.Done
import play.api.http.HttpEntity
import sttp.tapir.server.interpreter.BodyListener

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class PlayBodyListener(implicit ec: ExecutionContext) extends BodyListener[Future, PlayResponseBody] {
  override def onComplete(body: PlayResponseBody)(cb: Try[Unit] => Future[Unit]): Future[PlayResponseBody] = {

    def onDone(f: Future[Done]): Unit = f.onComplete {
      case Failure(ex) => cb(Failure(ex))
      case _           => cb(Success(()))
    }

    body match {
      case ws @ Left(_) => cb(Success(())).map(_ => ws)
      case Right(r) =>
        (r match {
          case e @ HttpEntity.Streamed(data, _, _) =>
            Future.successful(e.copy(data = data.watchTermination() { case (_, f) => onDone(f) }))
          case e @ HttpEntity.Chunked(chunks, _) =>
            Future.successful(e.copy(chunks = chunks.watchTermination() { case (_, f) => onDone(f) }))
          case e @ HttpEntity.Strict(_, _) => cb(Success(())).map(_ => e)
        }).map(Right(_))
    }
  }
}
