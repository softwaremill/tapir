package sttp.tapir.server

import fs2.Pipe
import org.http4s.EntityBody
import org.http4s.websocket.WebSocketFrame
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.sse.ServerSentEvent
import sttp.tapir.model.ServerRequest
import sttp.tapir.typelevel.ParamConcat
import sttp.tapir.{AttributeKey, CodecFormat, Endpoint, StreamBodyIO, extractFromRequest, streamTextBody}

import java.nio.charset.Charset
import scala.reflect.ClassTag

package object http4s {
  // either a web socket, or a stream with optional length (if known)
  private[http4s] type Http4sResponseBody[F[_]] = Either[F[Pipe[F, WebSocketFrame, WebSocketFrame]], (EntityBody[F], Option[Long])]

  def serverSentEventsBody[F[_]]: StreamBodyIO[fs2.Stream[F, Byte], fs2.Stream[F, ServerSentEvent], Fs2Streams[F]] = {
    val fs2Streams = Fs2Streams[F]
    streamTextBody(fs2Streams)(CodecFormat.TextEventStream(), Some(Charset.forName("UTF-8")))
      .map(Http4sServerSentEvents.parseBytesToSSE[F])(Http4sServerSentEvents.serialiseSSEToBytes[F])
  }

  private[http4s] def contextAttributeKey[T: ClassTag]: AttributeKey[T] = new AttributeKey(implicitly[ClassTag[T]].runtimeClass.getName)

  implicit class RichHttp4sEndpoint[A, I, E, O, R](e: Endpoint[A, I, E, O, R]) {

    /** Access the context provided by an http4s middleware, such as authentication data.
      *
      * Interpreting endpoints which access the http4s context requires the usage of the [[Http4sServerInterpreter.toContextRoutes]] method.
      * This then yields a [[org.http4s.ContextRoutes]] instance, which needs to be correctly mounted in the http4s router.
      *
      * Note that the correct syntax for adding the context input includes `()` after the method invocation, to properly infer types and
      * capture implicit parameters, e.g. `myEndpoint.contextIn[Auth]()`.
      */
    def contextIn[T]: AddContextInput[T] = new AddContextInput[T]

    /** Access the context provided by an http4s middleware, such as authentication data.
      *
      * Interpreting endpoints which access the http4s context requires the usage of the [[Http4sServerInterpreter.toContextRoutes]] method.
      * This then yields a [[org.http4s.ContextRoutes]] instance, which needs to be correctly mounted in the http4s router.
      *
      * Note that the correct syntax for adding the context input includes `()` after the method invocation, to properly infer types and
      * capture implicit parameters, e.g. `myEndpoint.contextSecurityIn[Auth]()`.
      */
    def contextSecurityIn[T]: AddContextSecurityInput[T] = new AddContextSecurityInput[T]

    class AddContextInput[T] {
      def apply[IT]()(implicit concat: ParamConcat.Aux[I, T, IT], ct: ClassTag[T]): Endpoint[A, IT, E, O, R with Context[T]] = {
        val attribute = contextAttributeKey[T]
        e.in(extractFromRequest[T](extractContext[T](attribute)))
      }
    }

    class AddContextSecurityInput[T] {
      def apply[AT]()(implicit concat: ParamConcat.Aux[A, T, AT], ct: ClassTag[T]): Endpoint[AT, I, E, O, R with Context[T]] = {
        val attribute = contextAttributeKey[T]
        e.securityIn(extractFromRequest[T](extractContext[T](attribute)))
      }
    }

    private def extractContext[T](attribute: AttributeKey[T]): ServerRequest => T = (req: ServerRequest) =>
      req
        .attribute(attribute)
        // should never happen since http4s had to build a ContextRequest with Ctx for ContextRoutes
        .getOrElse(throw new RuntimeException(s"context ${attribute.typeName} not found in the request"))
  }
}
