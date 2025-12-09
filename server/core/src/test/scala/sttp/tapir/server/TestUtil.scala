package sttp.tapir.server

import sttp.capabilities.Streams
import sttp.model.Uri._
import sttp.model._
import sttp.monad.{IdentityMonad, MonadError}
import sttp.shared.Identity
import sttp.tapir._
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interpreter.{BodyListener, RawValue, RequestBody, ToResponseBody}
import sttp.tapir.server.model.ServerResponse

import java.nio.charset.Charset
import scala.collection.immutable
import scala.util.{Success, Try}

object TestUtil {
  object TestRequestBody extends RequestBody[Identity, NoStreams] {
    override val streams: Streams[NoStreams] = NoStreams
    override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): Identity[RawValue[R]] = ???
    override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream = ???
  }

  object UnitToResponseBody extends ToResponseBody[Unit, NoStreams] {
    override val streams: Streams[NoStreams] = NoStreams
    override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): Unit = ()
    override def fromStreamValue(
        v: streams.BinaryStream,
        headers: HasHeaders,
        format: CodecFormat,
        charset: Option[Charset]
    ): Unit = ???
    override def fromWebSocketPipe[REQ, RESP](
        pipe: streams.Pipe[REQ, RESP],
        o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, ?, NoStreams]
    ): Unit = ???
  }

  object StringToResponseBody extends ToResponseBody[String, NoStreams] {
    override val streams: Streams[NoStreams] = NoStreams
    override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): String =
      v.asInstanceOf[String]
    override def fromStreamValue(v: streams.BinaryStream, headers: HasHeaders, format: CodecFormat, charset: Option[Charset]): String = ""
    override def fromWebSocketPipe[REQ, RESP](
        pipe: streams.Pipe[REQ, RESP],
        o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, ?, NoStreams]
    ): String = ""
  }

  implicit val unitBodyListener: BodyListener[Identity, Unit] = new BodyListener[Identity, Unit] {
    override def onComplete(body: Unit)(cb: Try[Unit] => Identity[Unit]): Unit = {
      cb(Success(()))
      ()
    }
  }

  implicit val stringBodyListener: BodyListener[Identity, String] = new BodyListener[Identity, String] {
    override def onComplete(body: String)(cb: Try[Unit] => Identity[Unit]): String = {
      cb(Success(()))
      body
    }
  }

  def createTestRequest(
      path: List[String],
      queryParams: Seq[(String, String)] = Nil,
      _method: Method = Method.GET
  ): ServerRequest = {
    val queryString = if (queryParams.nonEmpty) s"?${queryParams.map { case (k, v) => s"$k=$v" }.mkString("&")}" else ""
    val pathString = if (path.nonEmpty) s"/${path.mkString("/")}" else ""

    new ServerRequest {
      override def protocol: String = "HTTP/1.1"
      override def connectionInfo: ConnectionInfo = ConnectionInfo(None, None, None)
      override def underlying: Any = ()
      override def pathSegments: List[String] = path
      override def queryParameters: QueryParams = QueryParams.fromSeq(queryParams)
      override def method: Method = _method
      override def uri: Uri = uri"http://example.com$pathString$queryString"
      override def headers: immutable.Seq[Header] = Nil
      override def attribute[T](k: AttributeKey[T]): Option[T] = None
      override def attribute[T](k: AttributeKey[T], v: T): ServerRequest = this
      override def withUnderlying(underlying: Any): ServerRequest = this
    }
  }

  /** A test interceptor that appends messages to a call trail, useful for testing interceptor ordering.
    * @param addCallTrail
    *   Function to append a message to the trail
    * @param prefix
    *   Prefix to add to each message
    */
  class AddToTrailInterceptor(addCallTrail: String => Unit, prefix: String) extends EndpointInterceptor[Identity] {
    override def apply[B](responder: Responder[Identity, B], endpointHandler: EndpointHandler[Identity, B]): EndpointHandler[Identity, B] =
      new EndpointHandler[Identity, B] {
        override def onDecodeSuccess[A, U, I](
            ctx: DecodeSuccessContext[Identity, A, U, I]
        )(implicit monad: MonadError[Identity], bodyListener: BodyListener[Identity, B]): Identity[ServerResponse[B]] = {
          addCallTrail(s"$prefix success")
          endpointHandler.onDecodeSuccess(ctx)(monad, bodyListener)
        }

        override def onSecurityFailure[A](ctx: SecurityFailureContext[Identity, A])(implicit
            monad: MonadError[Identity],
            bodyListener: BodyListener[Identity, B]
        ): Identity[ServerResponse[B]] = {
          addCallTrail(s"$prefix security failure")
          endpointHandler.onSecurityFailure(ctx)(monad, bodyListener)
        }

        override def onDecodeFailure(
            ctx: DecodeFailureContext
        )(implicit monad: MonadError[Identity], bodyListener: BodyListener[Identity, B]): Identity[Option[ServerResponse[B]]] = {
          addCallTrail(s"$prefix failure")
          endpointHandler.onDecodeFailure(ctx)(monad, bodyListener)
        }
      }
  }
}
