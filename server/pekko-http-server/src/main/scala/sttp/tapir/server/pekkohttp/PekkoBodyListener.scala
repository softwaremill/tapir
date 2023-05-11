package sttp.tapir.server.pekkohttp

import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.util.ByteString
import sttp.tapir.server.interpreter.BodyListener

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class PekkoBodyListener(implicit ec: ExecutionContext) extends BodyListener[Future, PekkoResponseBody] {
  override def onComplete(body: PekkoResponseBody)(cb: Try[Unit] => Future[Unit]): Future[PekkoResponseBody] = {
    body match {
      case ws @ Left(_) => cb(Success(())).map(_ => ws)
      case Right(r) =>
        Future.successful(Right(r.transformDataBytes(Flow[ByteString].watchTermination() { case (_, f) =>
          f.onComplete {
            case Failure(ex) => cb(Failure(ex))
            case Success(_)  => cb(Success(()))
          }
        })))
    }
  }
}
