package sttp.tapir.server.play

import akka.NotUsed
import akka.stream.scaladsl.{FileIO, Flow, Source, StreamConverters}
import akka.util.ByteString
import play.api.http.websocket.{BinaryMessage, Message, TextMessage}
import play.api.http.{ContentTypes, HeaderNames, HttpEntity}
import play.api.mvc.MultipartFormData.{DataPart, FilePart}
import play.api.mvc.{Codec, MultipartFormData, ResponseHeader, Result}
import sttp.capabilities.akka.AkkaStreams
import sttp.model.{MediaType, Part, StatusCode}
import sttp.tapir.internal.ParamsAsAny
import sttp.tapir.server.internal.{EncodeOutputBody, EncodeOutputs, OutputValues}
import sttp.tapir.{CodecFormat, DecodeResult, EndpointOutput, RawBodyType, RawPart, WebSocketBodyOutput, WebSocketFrameDecodeFailure}
import sttp.ws.{WebSocketClosed, WebSocketFrame}

import java.io.{File, InputStream}
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.file.Files
import scala.concurrent.Future

private[play] object OutputToPlayResponse {
  private type EntityFromLength = Option[Long] => HttpEntity

  def apply[O](
      defaultStatus: StatusCode,
      output: EndpointOutput[O],
      v: O
  ): Result = {
    val outputValues = encodeOutputs(output, ParamsAsAny(v), OutputValues.empty)
    val headers: Map[String, String] = outputValues.headers
      .foldLeft(Map.empty[String, List[String]]) { (a, b) =>
        if (a.contains(b._1)) a + (b._1 -> (a(b._1) :+ b._2)) else a + (b._1 -> List(b._2))
      }
      .map {
        // See comment in play.api.mvc.CookieHeaderEncoding
        case (key, value) if key == HeaderNames.SET_COOKIE => (key, value.mkString(";;"))
        case (key, value)                                  => (key, value.mkString(", "))
      }
    val status = outputValues.statusCode.getOrElse(defaultStatus)

    outputValues.body match {
      case Some(Left(entityFromLength)) =>
        val entity = entityFromLength(outputValues.contentLength)
        val result = Result(ResponseHeader(status.code, headers), entity)
        headers.find(_._1.toLowerCase == "content-type").map(ct => result.as(ct._2)).getOrElse(result)
      case Some(Right(flow)) =>
        // TODO figure out how to convert `flow` to a play `Result`
//        val akkaResp = WebSocketHandler.handleWebSocket(???, flow, 0, None)
//        val result = Result(ResponseHeader(status.code, headers), akkaResp)
//        headers.find(_._1.toLowerCase == "content-type").map(ct => result.as(ct._2)).getOrElse(result)
        ???
      case None =>
        Result(ResponseHeader(status.code, headers), HttpEntity.NoEntity)
    }
  }

  private val encodeOutputs: EncodeOutputs[EntityFromLength, Flow[Message, Message, Any], AkkaStreams] =
    new EncodeOutputs[EntityFromLength, Flow[Message, Message, Any], AkkaStreams](
      new EncodeOutputBody[EntityFromLength, Flow[Message, Message, Any], AkkaStreams] {
        override val streams: AkkaStreams = AkkaStreams

        override def rawValueToBody[R](v: R, format: CodecFormat, bodyType: RawBodyType[R]): EntityFromLength =
          contentLength => rawValueToResponseEntity(bodyType.asInstanceOf[RawBodyType[Any]], contentLength, formatToContentType(format), v)

        override def streamValueToBody(v: Source[ByteString, Any], format: CodecFormat, charset: Option[Charset]): EntityFromLength =
          contentLength => HttpEntity.Streamed(v, contentLength, formatToContentType(format))

        override def webSocketPipeToBody[REQ, RESP](
            pipe: Flow[REQ, RESP, Any],
            o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, AkkaStreams]
        ): Flow[Message, Message, Any] = pipeToBody(pipe, o)
      }
    )

  private def rawValueToResponseEntity[R](
      bodyType: RawBodyType[R],
      contentLength: Option[Long],
      contentType: Option[String],
      r: R
  ): HttpEntity = {
    bodyType match {
      case RawBodyType.StringBody(charset) =>
        val str = r.asInstanceOf[String]
        HttpEntity.Strict(ByteString(str, charset), contentType)

      case RawBodyType.ByteArrayBody =>
        val bytes = r.asInstanceOf[Array[Byte]]
        HttpEntity.Strict(ByteString(bytes), contentType)

      case RawBodyType.ByteBufferBody =>
        val byteBuffer = r.asInstanceOf[ByteBuffer]
        HttpEntity.Strict(ByteString(byteBuffer), contentType)

      case RawBodyType.InputStreamBody =>
        val stream = r.asInstanceOf[InputStream]
        HttpEntity.Streamed(StreamConverters.fromInputStream(() => stream), contentLength, contentType)

      case RawBodyType.FileBody =>
        val path = r.asInstanceOf[File].toPath
        val fileSize = Some(Files.size(path))
        val file = FileIO.fromPath(path)
        HttpEntity.Streamed(file, fileSize, contentType)

      case m: RawBodyType.MultipartBody =>
        val rawParts = r.asInstanceOf[Seq[RawPart]]

        val dataParts = rawParts
          .filter { part =>
            m.partType(part.name).exists {
              case RawBodyType.StringBody(_)  => true
              case RawBodyType.ByteArrayBody  => true
              case RawBodyType.ByteBufferBody => true
              case _                          => false
            }
          }
          .flatMap(rawPartsToDataPart(m, _, contentLength))

        val fileParts = rawParts
          .filter { part =>
            m.partType(part.name).exists {
              case RawBodyType.InputStreamBody => true
              case RawBodyType.FileBody        => true
              case _                           => false
            }
          }
          .flatMap(rawPartsToFilePart(m, _, contentLength))

        HttpEntity.Streamed(multipartFormToStream(dataParts, fileParts), contentLength, contentType)
    }
  }

  private def rawPartsToFilePart[T](
      m: RawBodyType.MultipartBody,
      part: Part[T],
      contentLength: Option[Long]
  ): Option[MultipartFormData.FilePart[Source[ByteString, _]]] = {
    m.partType(part.name).flatMap { partType =>
      val entity: HttpEntity = rawValueToResponseEntity(partType.asInstanceOf[RawBodyType[Any]], contentLength, part.contentType, part.body)

      for {
        fileName <- part.fileName
        contentLength <- entity.contentLength
        dispositionType <- part.otherDispositionParams.get(part.name)
      } yield MultipartFormData.FilePart(part.name, fileName, entity.contentType, entity.dataStream, contentLength, dispositionType)
    }
  }

  private def rawPartsToDataPart[T](
      m: RawBodyType.MultipartBody,
      part: Part[T],
      contentLength: Option[Long]
  ): Option[MultipartFormData.DataPart] = {
    m.partType(part.name).flatMap { partType =>
      val charset = partType match {
        case valueType: RawBodyType.StringBody => valueType.charset
        case _                                 => Charset.defaultCharset()
      }

      val maybeData: Option[String] =
        rawValueToResponseEntity(partType.asInstanceOf[RawBodyType[Any]], contentLength, part.contentType, part.body) match {
          case HttpEntity.Strict(data, _)   => Some(data.decodeString(charset))
          case HttpEntity.Streamed(_, _, _) => None
          case HttpEntity.Chunked(_, _)     => None
        }

      maybeData.map(MultipartFormData.DataPart(part.name, _))
    }
  }

  private def formatToContentType(format: CodecFormat): Option[String] = {
    val result = format.mediaType.copy(charset = format.mediaType.charset.map(_.toLowerCase)) match {
      case MediaType.ApplicationJson               => ContentTypes.JSON
      case MediaType.TextPlain                     => ContentTypes.TEXT(Codec.javaSupported(format.mediaType.charset.getOrElse("utf-8")))
      case MediaType.TextPlainUtf8                 => ContentTypes.TEXT(Codec.utf_8)
      case MediaType.TextHtml                      => ContentTypes.HTML(Codec.utf_8)
      case MediaType.ApplicationOctetStream        => ContentTypes.BINARY
      case MediaType.ApplicationXWwwFormUrlencoded => ContentTypes.FORM
      case MediaType.MultipartFormData             => "multipart/form-data"
      case m                                       => m.toString()
    }

    Option(result)
  }

  private def multipartFormToStream[A](
      dataParts: Seq[DataPart],
      fileParts: Seq[FilePart[Source[ByteString, _]]]
  ): Source[ByteString, NotUsed] = {
    val boundary: String = "--------" + scala.util.Random.alphanumeric.take(20).mkString("")

    def formatDataParts(dataParts: Seq[DataPart]) = {
      val result = dataParts
        .flatMap { case DataPart(name, value) =>
          s"""
              --$boundary\r\n${HeaderNames.CONTENT_DISPOSITION}: form-data; name="$name"\r\n\r\n$value\r\n
            """.stripMargin
        }
        .mkString("")
      Codec.utf_8.encode(result)
    }

    def filePartHeader(file: FilePart[_]) = {
      val name = s""""${file.key}""""
      val filename = s""""${file.filename}""""
      val contentType = file.contentType
        .map { ct => s"${HeaderNames.CONTENT_TYPE}: $ct\r\n" }
        .getOrElse("")
      Codec.utf_8.encode(
        s"--$boundary\r\n${HeaderNames.CONTENT_DISPOSITION}: form-data; name=$name; filename=$filename\r\n$contentType\r\n"
      )
    }

    Source
      .single(formatDataParts(dataParts))
      .concat(Source(fileParts.toList).flatMapConcat { file =>
        Source
          .single(filePartHeader(file))
          .concat(file.ref)
          .concat(Source.single(ByteString("\r\n", Charset.forName("UTF-8"))))
          .concat(Source.single(ByteString(s"--$boundary--", "UTF-8")))
      })
  }

  private def pipeToBody[REQ, RESP](
      pipe: Flow[REQ, RESP, Any],
      o: WebSocketBodyOutput[Flow[REQ, RESP, Any], REQ, RESP, _, AkkaStreams]
  ): Flow[Message, Message, Any] = {

    def messageToFrame(m: Message) = m match {
      case msg: TextMessage =>
        Some(WebSocketFrame.text(msg.data))
      case msg: BinaryMessage =>
        Some(WebSocketFrame.binary(msg.data.toArray))
      case _ =>
        None
    }

    def frameToMessage(w: WebSocketFrame) = w match {
      case WebSocketFrame.Text(p, _, _)   => Some(TextMessage(p))
      case WebSocketFrame.Binary(p, _, _) => Some(BinaryMessage(ByteString(p)))
      case WebSocketFrame.Ping(_)         => None
      case WebSocketFrame.Pong(_)         => None
      case WebSocketFrame.Close(_, _)     => throw WebSocketClosed(None)
    }

    Flow[Message]
      .mapAsync(1)(msg => Future.successful(messageToFrame(msg)))
      .mapConcat(_.toList)
      .map(f =>
        o.requests.decode(f) match {
          case failure: DecodeResult.Failure => throw new WebSocketFrameDecodeFailure(f, failure)
          case DecodeResult.Value(v)         => v
        }
      )
      .via(pipe)
      .map(o.responses.encode)
      .takeWhile {
        case WebSocketFrame.Close(_, _) => false
        case _                          => true
      }
      .mapConcat(frameToMessage(_).toList)
  }
}
