package sttp.tapir.server.play

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString
import play.api.http.{HeaderNames, HttpEntity}
import play.api.mvc.RequestHeader
import sttp.tapir.AttributeKey

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal

/** Sends `100 Continue` for a request body stream which is read only after the response headers are written, e.g. when echoed.
  *
  * pekko-http sends `100 Continue` when the body is first pulled. If that happens after the response headers are written, the `100` is
  * queued after the response, and the client, waiting for it, never sends the body. Hence, a body stream given to the server logic is
  * pulled before completing a response with a streamed body.
  */
private[play] class PlayExpectContinue(implicit mat: Materializer, ec: ExecutionContext) {
  @volatile private var body: Option[Source[ByteString, Any]] = None
  private val started = new AtomicReference[Future[Source[ByteString, Any]]]()

  /** The `body` to give to the server logic. */
  def bodyGivenToServerLogic(body: Source[ByteString, Any]): Source[ByteString, Any] = {
    this.body = Some(body)
    Source.lazyFutureSource(() => start(body))
  }

  /** Completes when the response can be sent. If the response body might read the body given to the server logic, that's after the first
    * element of the latter is received, i.e. after `100 Continue` is sent.
    */
  def beforeResponse(responseBody: Option[PlayResponseBody]): Future[Unit] = (body, responseBody) match {
    case (Some(b), Some(Right(_: HttpEntity.Streamed | _: HttpEntity.Chunked))) =>
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

private[play] object PlayExpectContinue {
  val Key: AttributeKey[PlayExpectContinue] = AttributeKey[PlayExpectContinue]

  /** Defined if the client waits for `100 Continue` before sending the body. */
  def apply(request: RequestHeader)(implicit mat: Materializer, ec: ExecutionContext): Option[PlayExpectContinue] =
    if (request.headers.get(HeaderNames.EXPECT).exists(_.equalsIgnoreCase("100-continue"))) Some(new PlayExpectContinue) else None
}
