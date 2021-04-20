package sttp.tapir.server.interpreter

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.Streams
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.internal.NoStreams
import sttp.tapir.model.{ConnectionInfo, ServerRequest, ServerResponse}
import sttp.tapir.server.interceptor.{
  DecodeFailureContext,
  DecodeSuccessContext,
  EndpointHandler,
  EndpointInterceptor,
  RequestHandler,
  RequestInterceptor,
  Responder
}
import sttp.model._
import sttp.model.Uri._

import java.nio.charset.Charset
import scala.collection.immutable

class ServerInterpreterTest extends AnyFlatSpec with Matchers {
  type Id[X] = X

  object TestRequestBody extends RequestBody[Id, Nothing] {
    override val streams: Streams[Nothing] = NoStreams
    override def toRaw[R](bodyType: RawBodyType[R]): Id[RawValue[R]] = ???
    override def toStream(): streams.BinaryStream = ???
  }

  object TestToResponseBody extends ToResponseBody[Unit, Nothing] {
    override val streams: Streams[Nothing] = NoStreams
    override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): Unit = ???
    override def fromStreamValue(
        v: streams.BinaryStream,
        headers: HasHeaders,
        format: CodecFormat,
        charset: Option[Charset]
    ): Unit = ???
    override def fromWebSocketPipe[REQ, RESP](
        pipe: streams.Pipe[REQ, RESP],
        o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, Nothing]
    ): Unit = ???
  }

  implicit object IdMonadError extends MonadError[Id] {
    override def unit[T](t: T): Id[T] = t
    override def map[T, T2](fa: Id[T])(f: T => T2): Id[T2] = f(fa)
    override def flatMap[T, T2](fa: Id[T])(f: T => Id[T2]): Id[T2] = f(fa)
    override def error[T](t: Throwable): Id[T] = throw t
    override protected def handleWrappedError[T](rt: Id[T])(h: PartialFunction[Throwable, Id[T]]): Id[T] = rt
    override def ensure[T](f: Id[T], e: => Id[Unit]): Id[T] = try f
    finally e
  }

  it should "call the interceptors in the correct order" in {
    var callTrail: List[String] = Nil

    class AddToTrailInterceptor(prefix: String) extends EndpointInterceptor[Id, Unit] {
      override def apply(responder: Responder[Id, Unit], endpointHandler: EndpointHandler[Id, Unit]): EndpointHandler[Id, Unit] =
        new EndpointHandler[Id, Unit] {
          override def onDecodeSuccess[I](ctx: DecodeSuccessContext[Id, I])(implicit monad: MonadError[Id]): Id[ServerResponse[Unit]] = {
            callTrail ::= s"$prefix success"
            endpointHandler.onDecodeSuccess(ctx)
          }

          override def onDecodeFailure(ctx: DecodeFailureContext)(implicit monad: MonadError[Id]): Id[Option[ServerResponse[Unit]]] = {
            callTrail ::= s"$prefix failure"
            endpointHandler.onDecodeFailure(ctx)
          }
        }
    }

    val testRequest = new ServerRequest {
      override def protocol: String = ""
      override def connectionInfo: ConnectionInfo = ConnectionInfo(None, None, None)
      override def underlying: Any = ()
      override def pathSegments: List[String] = Nil
      override def queryParameters: QueryParams = QueryParams.fromSeq(List(("x", "1")))
      override def method: Method = Method.GET
      override def uri: Uri = uri"http://example.com"
      override def headers: immutable.Seq[Header] = Nil
    }

    // given
    val interceptor1 = new AddToTrailInterceptor("1")
    val interceptor2 = new RequestInterceptor[Id, Unit] {
      override def apply(
          responder: Responder[Id, Unit],
          requestHandler: EndpointInterceptor[Id, Unit] => RequestHandler[Id, Unit]
      ): RequestHandler[Id, Unit] = new RequestHandler[Id, Unit] {
        override def apply(request: ServerRequest): Id[Option[ServerResponse[Unit]]] = {
          callTrail ::= "2 request"
          requestHandler(new AddToTrailInterceptor("2")).apply(request)
        }
      }
    }
    val interceptor3 = new AddToTrailInterceptor("3")

    val interpreter =
      new ServerInterpreter[Any, Id, Unit, Nothing](TestRequestBody, TestToResponseBody, List(interceptor1, interceptor2, interceptor3))

    // when
    interpreter.apply(testRequest, endpoint.in(query[String]("x")).serverLogic[Id](_ => Right(())))

    // then
    callTrail.reverse shouldBe List("2 request", "1 success", "2 success", "3 success")
  }
}
