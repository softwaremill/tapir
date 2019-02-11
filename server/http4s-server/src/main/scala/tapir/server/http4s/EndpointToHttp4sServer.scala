package tapir.server.http4s

import java.io.ByteArrayInputStream

import cats.data._
import cats.effect.{ContextShift, Sync}
import cats.implicits._
import fs2.Chunk
import org.http4s
import org.http4s.headers.{`Content-Disposition`, `Content-Type`}
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Charset, EntityBody, EntityDecoder, EntityEncoder, Header, Headers, HttpRoutes, Request, Response, Status, multipart}
import tapir.internal.server.{DecodeInputs, DecodeInputsResult, InputValues}
import tapir.internal.{ParamsToSeq, SeqToParams}
import tapir.server.{DecodeFailureHandling, StatusMapper}
import tapir.typelevel.ParamsAsArgs
import tapir.{
  ByteArrayValueType,
  ByteBufferValueType,
  CodecMeta,
  DecodeFailure,
  DecodeResult,
  Endpoint,
  EndpointIO,
  EndpointInput,
  FileValueType,
  InputStreamValueType,
  MediaType,
  MultipartValueType,
  Part,
  RawPart,
  RawValueType,
  StatusCode,
  StreamingEndpointIO,
  StringValueType
}

class EndpointToHttp4sServer[F[_]: Sync: ContextShift](serverOptions: Http4sServerOptions[F]) {

  private val logger = org.log4s.getLogger
  private val http4sMethodToTapirMethodMap: Map[org.http4s.Method, tapir.Method] = {
    import org.http4s.Method._
    import tapir.Method
    Map(
      GET -> Method.GET,
      POST -> Method.POST,
      DELETE -> Method.DELETE,
      PUT -> Method.PUT,
      OPTIONS -> Method.OPTIONS,
      PATCH -> Method.PATCH,
      CONNECT -> Method.CONNECT
    )
  }

  private def mediaTypeToContentType(mediaType: MediaType): `Content-Type` =
    mediaType match {
      case MediaType.Json()               => `Content-Type`(http4s.MediaType.application.json)
      case MediaType.TextPlain(charset)   => `Content-Type`(http4s.MediaType.text.plain, Charset.fromNioCharset(charset))
      case MediaType.OctetStream()        => `Content-Type`(http4s.MediaType.application.`octet-stream`)
      case MediaType.XWwwFormUrlencoded() => `Content-Type`(http4s.MediaType.application.`x-www-form-urlencoded`)
      case MediaType.MultipartFormData()  => `Content-Type`(http4s.MediaType.multipart.`form-data`)
      case mt                             => `Content-Type`(http4s.MediaType.parse(mt.mediaType).right.get) // TODO
    }

  private def rawValueToEntity[M <: MediaType, R](codecMeta: CodecMeta[M, R], r: R): (EntityBody[F], Header) = {
    val ct: `Content-Type` = mediaTypeToContentType(codecMeta.mediaType)

    codecMeta.rawValueType match {
      case StringValueType(charset) =>
        val bytes = r.toString.getBytes(charset)
        fs2.Stream.chunk(Chunk.bytes(bytes)) -> ct
      case ByteArrayValueType  => fs2.Stream.chunk(Chunk.bytes(r)) -> ct
      case ByteBufferValueType => fs2.Stream.chunk(Chunk.byteBuffer(r)) -> ct
      case InputStreamValueType =>
        fs2.io.readInputStream(r.pure[F], serverOptions.ioChunkSize, serverOptions.blockingExecutionContext) -> ct
      case FileValueType => fs2.io.file.readAll(r.toPath, serverOptions.blockingExecutionContext, serverOptions.ioChunkSize) -> ct
      case mvt: MultipartValueType =>
        val parts = (r: Seq[RawPart]).flatMap(rawPartToBodyPart(mvt, _))
        val body = implicitly[EntityEncoder[F, multipart.Multipart[F]]].toEntity(multipart.Multipart(parts.toVector)).body
        body -> ct
    }
  }

  private def rawPartToBodyPart[T](mvt: MultipartValueType, part: Part[T]): Option[multipart.Part[F]] = {
    mvt.partCodecMeta(part.name).map { codecMeta =>
      val headers = part.headers.map {
        case (hk, hv) => Header.Raw(CaseInsensitiveString(hk), hv)
      }.toList

      val (entity, ctHeader) = rawValueToEntity(codecMeta.asInstanceOf[CodecMeta[_ <: MediaType, Any]], part.body)

      val contentDispositionHeader = `Content-Disposition`("form-data", part.otherDispositionParams + ("name" -> part.name))

      multipart.Part(Headers(ctHeader :: contentDispositionHeader :: headers), entity)
    }
  }

  private case class OutputValues(body: Option[EntityBody[F]], headers: Vector[Header])

  private def singleOutputsWithValues(outputs: Vector[EndpointIO.Single[_]], v: Any): State[OutputValues, Unit] = {
    val vs = ParamsToSeq(v)

    val states = outputs.zipWithIndex.map {
      case (EndpointIO.Body(codec, _), i) =>
        codec.encode(vs(i)).map(rawValueToEntity(codec.meta, _)) match {
          // TODO: check if body isn't already set
          case Some((entity, header)) => State.modify[OutputValues](ov => ov.copy(body = Some(entity), headers = ov.headers :+ header))
          case None                   => State.pure[OutputValues, Unit](())
        }

      case (EndpointIO.StreamBodyWrapper(StreamingEndpointIO.Body(_, mediaType, _)), i) =>
        val ctHeader = mediaTypeToContentType(mediaType)
        State.modify[OutputValues](ov => ov.copy(body = Some(vs(i).asInstanceOf[EntityBody[F]]), headers = ov.headers :+ ctHeader))

      case (EndpointIO.Header(name, codec, _), i) =>
        codec
          .encode(vs(i))
          .map((headerValue: String) => Header.Raw(CaseInsensitiveString(name), headerValue)) match {
          case Nil     => State.pure[OutputValues, Unit](())
          case headers => State.modify[OutputValues](ov => ov.copy(headers = ov.headers ++ headers))
        }

      case (EndpointIO.Headers(_), i) =>
        val headers = vs(i).asInstanceOf[Seq[(String, String)]].map(h => Header.Raw(CaseInsensitiveString(h._1), h._2))
        State.modify[OutputValues](ov => ov.copy(headers = ov.headers ++ headers))

      case (EndpointIO.Mapped(wrapped, _, g, _), i) =>
        singleOutputsWithValues(wrapped.asVectorOfSingle, g(vs(i)))
    }

    states.sequence_
  }

  def toRoutes[I, E, O, FN[_]](e: Endpoint[I, E, O, EntityBody[F]])(
      logic: FN[F[Either[E, O]]],
      statusMapper: StatusMapper[O],
      errorStatusMapper: StatusMapper[E])(implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): HttpRoutes[F] = {

    val inputs: Vector[EndpointInput.Single[_]] = e.input.asVectorOfSingle
    logger.debug(s"Inputs: ")
    logger.debug(inputs.mkString("\n"))

    val service: HttpRoutes[F] = HttpRoutes[F] { req: Request[F] =>
      def decodeBody(result: DecodeInputsResult): F[DecodeInputsResult] = {
        result match {
          case values: DecodeInputsResult.Values =>
            values.bodyInput match {
              case Some(bodyInput @ EndpointIO.Body(codec, _)) =>
                basicRequestBody(req.body, codec.meta.rawValueType, req.charset, req).map { v =>
                  codec.decode(Some(v)) match {
                    case DecodeResult.Value(bodyV) => values.value(bodyInput, bodyV)
                    case failure: DecodeFailure    => DecodeInputsResult.Failure(bodyInput, failure): DecodeInputsResult
                  }
                }

              case None => (values: DecodeInputsResult).pure[F]
            }
          case failure: DecodeInputsResult.Failure => (failure: DecodeInputsResult).pure[F]
        }
      }

      def valuesToResponse(values: DecodeInputsResult.Values): F[Response[F]] = {
        val i = SeqToParams(InputValues(e.input, values.values)).asInstanceOf[I]
        paramsAsArgs
          .applyFn(logic, i)
          .map {
            case Right(result) =>
              makeResponse(statusCodeToHttp4sStatus(statusMapper(result)), e.output, result)
            case Left(err) =>
              makeResponse(statusCodeToHttp4sStatus(errorStatusMapper(err)), e.errorOutput, err)
          }
      }

      val methodMatches = http4sMethodToTapirMethodMap.get(req.method).contains(e.method)

      if (methodMatches) {
        OptionT(decodeBody(DecodeInputs(e.input, new Http4sDecodeInputsContext[F](req))).flatMap {
          case values: DecodeInputsResult.Values          => valuesToResponse(values).map(_.some)
          case DecodeInputsResult.Failure(input, failure) => handleDecodeFailure(req, input, failure).pure[F]
        })
      } else {
        OptionT.none
      }
    }

    service
  }

  private def statusCodeToHttp4sStatus(code: tapir.StatusCode): Status = Status.fromInt(code).right.get

  private def basicRequestBody[R](body: fs2.Stream[F, Byte],
                                  rawBodyType: RawValueType[R],
                                  charset: Option[Charset],
                                  req: Request[F]): F[R] = {
    def asChunk: F[Chunk[Byte]] = body.compile.toChunk
    def asByteArray: F[Array[Byte]] = body.compile.toChunk.map(_.toByteBuffer.array())

    rawBodyType match {
      case StringValueType(defaultCharset) => asByteArray.map(new String(_, charset.map(_.nioCharset).getOrElse(defaultCharset)))
      case ByteArrayValueType              => asByteArray
      case ByteBufferValueType             => asChunk.map(_.toByteBuffer)
      case InputStreamValueType            => asByteArray.map(new ByteArrayInputStream(_))
      case FileValueType =>
        serverOptions.createFile(serverOptions.blockingExecutionContext, req).flatMap { file =>
          val fileSink = fs2.io.file.writeAll(file.toPath, serverOptions.blockingExecutionContext)
          body.through(fileSink).compile.drain.map(_ => file)
        }
      case mvt: MultipartValueType =>
        // TODO: use MultipartDecoder.mixedMultipart once available?
        implicitly[EntityDecoder[F, multipart.Multipart[F]]].decode(req, strict = false).value.flatMap {
          case Left(failure) =>
            throw new IllegalArgumentException("Cannot decode multipart body: " + failure) // TODO
          case Right(mp) =>
            val rawPartsF: Vector[F[RawPart]] = mp.parts
              .flatMap(part => part.name.flatMap(name => mvt.partCodecMeta(name)).map((part, _)).toList)
              .map { case (part, codecMeta) => toRawPart(part, codecMeta, req).asInstanceOf[F[RawPart]] }

            val rawParts: F[Vector[RawPart]] = rawPartsF.sequence

            rawParts.asInstanceOf[F[R]] // R is Seq[RawPart]
        }
    }
  }

  private def toRawPart[R](part: multipart.Part[F], codecMeta: CodecMeta[_, R], req: Request[F]): F[Part[R]] = {
    val dispositionParams = part.headers.get(`Content-Disposition`).map(_.parameters).getOrElse(Map.empty)
    val charset = part.headers.get(`Content-Type`).flatMap(_.charset)
    basicRequestBody(part.body, codecMeta.rawValueType, charset, req)
      .map(r => Part(part.name.getOrElse(""), dispositionParams - "name", part.headers.map(h => (h.name.value, h.value)).toSeq, r))
  }

  private def makeResponse[O](statusCode: org.http4s.Status, output: EndpointIO[O], v: O): Response[F] = {
    val outputValues: OutputValues =
      singleOutputsWithValues(output.asVectorOfSingle, v).runS(OutputValues(None, Vector.empty)).value

    val headers = Headers(outputValues.headers: _*)
    outputValues.body match {
      case Some(entity) => Response(status = statusCode, headers = headers, body = entity)
      case None         => Response(status = statusCode, headers = headers)
    }
  }

  private def handleDecodeFailure[I](req: Request[F], input: EndpointInput.Single[_], failure: DecodeFailure): Option[Response[F]] = {
    val handling = serverOptions.decodeFailureHandler(req, input, failure)
    handling match {
      case DecodeFailureHandling.NoMatch => None
      case DecodeFailureHandling.RespondWithResponse(statusCode, body, codec) =>
        val (entity, header) = rawValueToEntity(codec.meta, codec.encode(body))
        Some(Response(status = statusCodeToHttp4sStatus(statusCode), headers = Headers(header), body = entity))
    }
  }
}
