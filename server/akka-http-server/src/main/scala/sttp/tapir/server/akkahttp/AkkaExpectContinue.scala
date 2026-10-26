package sttp.tapir.server.akkahttp

import akka.NotUsed
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.Expect
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import sttp.tapir.AttributeKey

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal

/** Sends `100 Continue` for a request body stream which is read only after the response headers are written, e.g. when echoed.
  *
  * akka-http sends `100 Continue` when the body is first pulled. If that happens after the response headers are written, the `100` is
  * queued after the response, and the client, waiting for it, never sends the body. Hence, a body stream given to the server logic is
  * pulled before completing a response with a streamed body.
  */
private[akkahttp] class AkkaExpectContinue(implicit mat: Materializer, ec: ExecutionContext) {
  @volatile private var body: Option[Source[ByteString, Any]] = None
  private val started = new AtomicReference[Future[Source[ByteString, Any]]]()

  /** The `body` to give to the server logic. */
  def bodyGivenToServerLogic(body: Source[ByteString, Any]): Source[ByteString, NotUsed] = {
    this.body = Some(body)
    Source.lazyFutureSource(() => start(body)).mapMaterializedValue(_ => NotUsed)
  }

  /** Completes when the response can be sent. If the response body might read the body given to the server logic, that's after the first
    * element of the latter is received, i.e. after `100 Continue` is sent.
    */
  def beforeResponse(responseBody: Option[AkkaResponseBody]): Future[Unit] = (body, responseBody) match {
    case (Some(b), Some(Right(entity))) if !entity.isStrict =>
      start(b).map(_ => ()).recover { case NonFatal(_) => () } // errors are reported when the body is read
    // if the body is not read yet, the response is sent without `100 Continue`, so the client does not send it
    case _ => Future.successful(())
  }

  // pulls the first element, keeping it until the returned stream is read
  private def start(body: Source[ByteString, Any]): Future[Source[ByteString, Any]] = {
    val p = Promise[Source[ByteString, Any]]()
    if (started.compareAndSet(null, p.future))
      p.completeWith(body.prefixAndTail(1).map { case (first, rest) => Source(first) ++ rest }.runWith(Sink.head))
    started.get()
  }
}

private[akkahttp] object AkkaExpectContinue {
  val Key: AttributeKey[AkkaExpectContinue] = AttributeKey[AkkaExpectContinue]

  /** Defined if the client waits for `100 Continue` before sending the body. */
  def apply(request: HttpRequest)(implicit mat: Materializer, ec: ExecutionContext): Option[AkkaExpectContinue] =
    if (request.header[Expect].isDefined && !request.entity.isStrict) Some(new AkkaExpectContinue) else None
}
