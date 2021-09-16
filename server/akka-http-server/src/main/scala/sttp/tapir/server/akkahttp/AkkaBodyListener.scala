package sttp.tapir.server.akkahttp

import akka.stream.scaladsl.Flow
import akka.util.ByteString
import sttp.tapir.server.interpreter.BodyListener

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class AkkaBodyListener(implicit ec: ExecutionContext) extends BodyListener[Future, AkkaResponseBody] {
  override def onComplete(body: AkkaResponseBody)(cb: Try[Unit] => Future[Unit]): Future[AkkaResponseBody] = {
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
