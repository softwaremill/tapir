package sttp.tapir.server.akkahttp

import akka.stream.scaladsl.Flow
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.UniversalEntity
import akka.util.ByteString
import sttp.tapir.server.interpreter.BodyListener

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class AkkaBodyListener(implicit ec: ExecutionContext) extends BodyListener[Future, AkkaResponseBody] {
  override def onComplete(body: AkkaResponseBody)(cb: Try[Unit] => Future[Unit]): Future[AkkaResponseBody] = {
    body match {
      case ws @ Left(_)               => cb(Success(())).map(_ => ws)
      case Right(e) if e.isKnownEmpty =>
        Future.successful(Right(e)).andThen { case _ => cb(Success(())) }
      case Right(e: UniversalEntity) =>
        Future.successful(
          Right(
            e.transformDataBytes(
              e.contentLength,
              Flow[ByteString].watchTermination() { case (_, f) =>
                f.onComplete {
                  case Failure(ex) => cb(Failure(ex))
                  case Success(_)  => cb(Success(()))
                }
              }
            )
          )
        )
      case Right(e) =>
        Future.successful(Right(e.transformDataBytes(Flow[ByteString].watchTermination() { case (_, f) =>
          f.onComplete {
            case Failure(ex) => cb(Failure(ex))
            case Success(_)  => cb(Success(()))
          }
        })))
    }
  }
}
