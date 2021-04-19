package sttp.tapir.server.zhttp

import io.netty.handler.codec.http.HttpResponseStatus
import sttp.capabilities
import sttp.capabilities.zio.ZioStreams
import sttp.model.{HasHeaders, QueryParams, Uri, Header => SttpHeader, Method => SttpMethod}
import sttp.monad.MonadError
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import sttp.tapir.server.interpreter.{RequestBody, ServerInterpreter, ToResponseBody}
import sttp.tapir.{CodecFormat, Endpoint, RawBodyType, WebSocketBodyOutput}
import zhttp.http.{CanSupportPartial, Http, HttpChannel, HttpData, Request, Response, Status, Header => ZHttpHeader}
import zio._
import zio.stream._

import java.net.InetSocketAddress
import java.nio.charset.Charset
import scala.collection.immutable.Seq

class ZHttpServerRequest(req: Request) extends ServerRequest {
  def protocol: String = "HTTP/1.1" //TODO

  def local =
    for {
      host <- req.url.host
      port <- req.url.port
    } yield new InetSocketAddress(host, port)

  lazy val connectionInfo: ConnectionInfo =
    ConnectionInfo(local, None, None)

  def underlying: Any = req

  lazy val pathSegments: List[String] = req.url.path.toList
  lazy val queryParameters: QueryParams = QueryParams.fromMap(Map.empty) // TODO: parse

  def method: SttpMethod = SttpMethod(req.method.asJHttpMethod.name().toUpperCase)

  def uri: Uri = Uri.unsafeParse(req.url.toString()) //TODO: is this correct?

  lazy val headers: Seq[SttpHeader] = req.headers.map(h => SttpHeader(h.name.toString, h.value.toString))
}

class ZHttpRequestBody[R](request: Request) extends RequestBody[RIO[R, *], ZioStreams] {
  override val streams: capabilities.Streams[ZioStreams] = ZioStreams

  def toRaw[R](bodyType: RawBodyType[R]): Task[R] = bodyType match {
    case RawBodyType.StringBody(_) =>
      ???
    case RawBodyType.ByteArrayBody =>
      ???
    case RawBodyType.ByteBufferBody => ???
    case RawBodyType.InputStreamBody => ???
    case RawBodyType.FileBody => ???
    case RawBodyType.MultipartBody(partTypes, defaultType) => ???
  }

  def toBytes(charset: Charset): ZTransducer[Any, Nothing, String, Byte] =
    ZTransducer.fromPush {
      case Some(chunk) => ZIO.succeed(chunk.flatMap(_.getBytes(charset)))
      case None => ZIO.succeed(Chunk.empty)
    }

  val stream: Stream[Throwable, Byte] = request.data.content match {
    case HttpData.Empty => ZStream.empty
    case HttpData.CompleteData(data) => ZStream.fromChunk(data)
    case HttpData.StreamData(stream) => stream
  }

  def toStream(): streams.BinaryStream = stream.asInstanceOf[streams.BinaryStream] //TODO: this is weird we need a aggressive type cast
}

class ZHttpToResponseBody extends ToResponseBody[Stream[Nothing, Byte], ZioStreams] {
  val streams: capabilities.Streams[ZioStreams] = ZioStreams

  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): Stream[Nothing, Byte] =
    rawValueToEntity(bodyType, v)

  override def fromStreamValue(v: streams.BinaryStream, headers: HasHeaders, format: CodecFormat, charset: Option[Charset]): Stream[Nothing, Byte] =
    v.asInstanceOf[Stream[Throwable, Byte]].refineOrDie(PartialFunction.empty) //TODO

  override def fromWebSocketPipe[REQ, RESP](pipe: streams.Pipe[REQ, RESP], o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, ZioStreams]): Stream[Nothing, Byte] =
    Stream.empty //TODO

  private def rawValueToEntity[CF <: CodecFormat, R](bodyType: RawBodyType[R], r: R): Stream[Nothing, Byte] = {
    bodyType match {
      case RawBodyType.StringBody(charset) =>
        ZStream.fromIterable(r.toString.getBytes(charset))
      case _ =>
        ZStream.empty
    }
  }
}

object ZHttpInterpreter {

  implicit def monadError[R]: MonadError[RIO[R, *]] = new MonadError[RIO[R, *]] {
    def unit[T](t: T): RIO[R, T] = ZIO.succeed(t)
    def map[T, T2](fa: RIO[R, T])(f: T => T2): RIO[R, T2] = fa.map(f)
    def flatMap[T, T2](fa: RIO[R, T])(f: T => RIO[R, T2]): RIO[R, T2] = fa.flatMap(f)
    def error[T](t: Throwable): RIO[R, T] = ZIO.fail(t)
    protected def handleWrappedError[T](rt: RIO[R, T])(h: PartialFunction[Throwable, RIO[R, T]]): RIO[R, T] = rt.catchSome(h)
    def ensure[T](f: RIO[R, T], e: => RIO[R, Unit]): RIO[R, T] = f.ensuring(e.orDie)
  }

  private def sttpToZHttpHeader(header: SttpHeader): ZHttpHeader =
    ZHttpHeader(header.name, header.value)

  def toHttp[I, O, R](route: Endpoint[I, Throwable, O, ZioStreams])(logic: I => RIO[R, O]): Http[R, Throwable] = {
    HttpChannel.fromEffectFunction[Request] { req =>
      val router = route.serverLogic[RIO[R, *]](input => logic(input).either)
      val interpreter = new ServerInterpreter[ZioStreams, RIO[R, *], Stream[Nothing, Byte], ZioStreams](
        new ZHttpRequestBody(req),
        new ZHttpToResponseBody,
        Nil
      )

      interpreter.apply(new ZHttpServerRequest(req), router).flatMap {
        case Some(resp) =>
          ZIO.succeed(
            Response.HttpResponse(
              status = Status.fromJHttpResponseStatus(HttpResponseStatus.valueOf(resp.code.code)),
              headers = resp.headers.map(sttpToZHttpHeader).toList,
              content = resp.body.map(stream => HttpData.fromStream(stream)).getOrElse(HttpData.empty)
            )
          )
        case None =>
          ZIO.fail(CanSupportPartial.HttpPartial.get(req))
      }

    }
  }
}
