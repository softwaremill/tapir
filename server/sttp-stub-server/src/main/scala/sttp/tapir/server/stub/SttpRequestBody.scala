package sttp.tapir.server.stub

import sttp.client3.{ByteArrayBody, ByteBufferBody, FileBody, InputStreamBody, MultipartBody, NoBody, Request, StreamBody, StringBody}
import sttp.monad.MonadError
import sttp.tapir.InputStreamRange
import sttp.tapir.RawBodyType
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{RawValue, RequestBody}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File, InputStream}
import java.nio.ByteBuffer
import scala.annotation.tailrec
import sttp.client3
import sttp.model.Part
import sttp.model.MediaType
import sttp.tapir.FileRange
import java.nio.file.Files
import java.io.FileInputStream


class SttpRequestBody[F[_]](implicit ME: MonadError[F]) extends RequestBody[F, AnyStreams] {
  override val streams: AnyStreams = AnyStreams

  override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): F[RawValue[R]] =
    body(serverRequest) match {
      case Left(bytes) =>
        bodyType match {
          case RawBodyType.StringBody(charset)  => ME.unit(RawValue(new String(bytes, charset)))
          case RawBodyType.ByteArrayBody        => ME.unit(RawValue(bytes))
          case RawBodyType.ByteBufferBody       => ME.unit(RawValue(ByteBuffer.wrap(bytes)))
          case RawBodyType.InputStreamBody      => ME.unit(RawValue(new ByteArrayInputStream(bytes)))
          case RawBodyType.FileBody             => ME.error(new UnsupportedOperationException)
          case RawBodyType.InputStreamRangeBody => ME.unit(RawValue(InputStreamRange(() => new ByteArrayInputStream(bytes))))
          case _: RawBodyType.MultipartBody     => ME.error(new UnsupportedOperationException)
        }
      case Right(value) =>
        bodyType match {
          case mp: RawBodyType.MultipartBody =>
            ME.unit(RawValue(extractMultipartParts(value.asInstanceOf[Seq[Part[client3.RequestBody[_]]]], mp)))
          case _ => throw new IllegalArgumentException("Stream body provided while endpoint accepts raw body type")
        }
    }

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream = body(serverRequest) match {
    case Right(stream) => stream
    case _             => throw new IllegalArgumentException("Raw body provided while endpoint accepts stream body")
  }

  private def sttpRequest(serverRequest: ServerRequest) = serverRequest.underlying.asInstanceOf[Request[_, _]]

  private def body(serverRequest: ServerRequest): Either[Array[Byte], Any] = sttpRequest(serverRequest).body match {
    case NoBody                     => Left(Array.emptyByteArray)
    case StringBody(s, encoding, _) => Left(s.getBytes(encoding))
    case ByteArrayBody(b, _)        => Left(b)
    case ByteBufferBody(b, _)       => Left(b.array())
    case InputStreamBody(b, _)      => Left(toByteArray(b))
    case FileBody(f, _)             => Left(f.readAsByteArray)
    case StreamBody(s)              => Right(s)
    case MultipartBody(parts)       => Right(parts)
  }

  private def toByteArray(is: InputStream): Array[Byte] = {
    val os = new ByteArrayOutputStream
    var read = 0
    val buf = new Array[Byte](1024)

    @tailrec
    def transfer(): Unit = {
      read = is.read(buf, 0, buf.length)
      if (read != -1) {
        os.write(buf, 0, read)
        transfer()
      }
    }

    transfer()
    os.toByteArray
  }

  private def extractMultipartParts(parts: Seq[Part[client3.RequestBody[_]]], bodyType: RawBodyType.MultipartBody): List[Part[Any]] = {
    parts.flatMap { part =>
      bodyType.partType(part.name).flatMap { partType =>
        extractPartBody(part, partType).map { body =>
          Part(
            name = part.name,
            body = body,
            contentType = part.contentType.flatMap(ct => MediaType.parse(ct).toOption),
            fileName = part.fileName
          )
        }
      }
    }.toList
  }

  private def extractPartBody[B](part: Part[client3.RequestBody[_]], bodyType: RawBodyType[B]): Option[Any] = {
    part.body match {
      case ByteArrayBody(b, _) =>
        bodyType match {
          case RawBodyType.StringBody(charset)  => Some(b)
          case RawBodyType.ByteArrayBody        => Some(b)
          case RawBodyType.ByteBufferBody       => Some(ByteBuffer.wrap(b))
          case RawBodyType.InputStreamBody      => Some(new ByteArrayInputStream(b))
          case RawBodyType.InputStreamRangeBody => Some(InputStreamRange(() => new ByteArrayInputStream(b)))
          case RawBodyType.FileBody             => throw new IllegalArgumentException("ByteArray body provided while endpoint accepts FileBody")
          case _: RawBodyType.MultipartBody     => None
        }
      case FileBody(f, _) =>
        bodyType match {
          case RawBodyType.FileBody        => Some(FileRange(f.toFile))
          case RawBodyType.ByteArrayBody   => Some(Files.readAllBytes(f.toPath))
          case RawBodyType.ByteBufferBody  => Some(ByteBuffer.wrap(Files.readAllBytes(f.toPath)))
          case RawBodyType.InputStreamBody => Some(new FileInputStream(f.toFile))
          case _                           => None
        }
      case StringBody(s, charset, _) =>
        bodyType match {
          case RawBodyType.StringBody(_)  => Some(s)
          case RawBodyType.ByteArrayBody  => Some(s.getBytes(charset))
          case RawBodyType.ByteBufferBody => Some(ByteBuffer.wrap(s.getBytes(charset)))
          case _                          => None
        }
      case InputStreamBody(is, _) =>
        bodyType match {
          case RawBodyType.InputStreamBody => Some(is)
          case _                           => None
        }
      case _ => None
    }
  }
}
