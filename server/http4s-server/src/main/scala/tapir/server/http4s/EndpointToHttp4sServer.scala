package tapir.server.http4s

import java.io.ByteArrayInputStream

import cats.data._
import cats.effect.{ContextShift, Sync}
import cats.implicits._
import fs2.Chunk
import org.http4s
import org.http4s.headers.`Content-Type`
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Charset, EntityBody, Header, Headers, HttpRoutes, Request, Response, Status}
import tapir.internal.{ParamsToSeq, SeqToParams}
import tapir.typelevel.ParamsAsArgs
import tapir.{
  ByteArrayValueType,
  ByteBufferValueType,
  Endpoint,
  EndpointIO,
  EndpointInput,
  FileValueType,
  GeneralCodec,
  InputStreamValueType,
  MediaType,
  RawValueType,
  StatusCode,
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
      case mt                             => `Content-Type`(http4s.MediaType.parse(mt.mediaType).right.get) // TODO
    }

  private def encodeBody[T, M <: MediaType, R](v: T, codec: GeneralCodec[T, M, R]): Option[(EntityBody[F], Header)] = {
    val ct: `Content-Type` = mediaTypeToContentType(codec.mediaType)

    codec.encodeOptional(v).map { r: R =>
      codec.rawValueType match {
        case StringValueType(charset) =>
          val bytes = r.toString.getBytes(charset)
          fs2.Stream.chunk(Chunk.bytes(bytes)) -> ct
        case ByteArrayValueType  => fs2.Stream.chunk(Chunk.bytes(r)) -> ct
        case ByteBufferValueType => fs2.Stream.chunk(Chunk.byteBuffer(r)) -> ct
        case InputStreamValueType =>
          fs2.io.readInputStream(r.pure[F], serverOptions.ioChunkSize, serverOptions.blockingExecutionContext) -> ct
        case FileValueType => fs2.io.file.readAll(r.toPath, serverOptions.blockingExecutionContext, serverOptions.ioChunkSize) -> ct
      }
    }
  }

  private case class OutputValues(body: Option[EntityBody[F]], headers: Vector[Header])

  private def singleOutputsWithValues(outputs: Vector[EndpointIO.Single[_]], v: Any): State[OutputValues, Unit] = {
    val vs = ParamsToSeq(v)

    val states = outputs.zipWithIndex.map {
      case (EndpointIO.Body(codec, _, _), i) =>
        encodeBody(vs(i), codec) match {
          // TODO: check if body isn't already set
          case Some((entity, header)) => State.modify[OutputValues](ov => ov.copy(body = Some(entity), headers = ov.headers :+ header))
          case None                   => State.pure[OutputValues, Unit](())
        }

      case (EndpointIO.Header(name, codec, _, _), i) =>
        codec
          .encodeOptional(vs(i))
          .map((headerValue: String) => Header.Raw(CaseInsensitiveString(name), headerValue)) match {
          case Some(header) => State.modify[OutputValues](ov => ov.copy(headers = ov.headers :+ header))
          case None         => State.pure[OutputValues, Unit](())
        }

      case (EndpointIO.Mapped(wrapped, _, g, _), i) =>
        singleOutputsWithValues(wrapped.asVectorOfSingle, g(vs(i)))
    }

    states.sequence_
  }

  def toRoutes[I, E, O, FN[_]](e: Endpoint[I, E, O])(
      logic: FN[F[Either[E, O]]],
      statusMapper: O => StatusCode,
      errorStatusMapper: E => StatusCode)(implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): HttpRoutes[F] = {

    val inputs: Vector[EndpointInput.Single[_]] = e.input.asVectorOfSingle
    logger.debug(s"Inputs: ")
    logger.debug(inputs.mkString("\n"))

    val service: HttpRoutes[F] = HttpRoutes[F] { req: Request[F] =>
      val context: F[Context[F]] = createContext(e, req)

      val inputMatch: ContextState[F] = new Http4sInputMatcher().matchInputs(inputs)

      val methodMatches: Either[Error, String] =
        Either.cond(http4sMethodToTapirMethodMap
                      .get(req.method)
                      .contains(e.method),
                    "",
                    s"Method mismatch: got ${req.method}, expected: ${e.method}")

      val value: F[Either[Error, (Context[F], MatchResult[F])]] =
        EitherT
          .fromEither[F](methodMatches)
          .flatMap(_ =>
            EitherT(context
              .map(inputMatch.run)))
          .value

      logger.debug(s"Result of binding: $value")

      val maybeMatch: OptionT[F, I] = OptionT(value.map(_.toOption.map {
        case (context2, result) =>
          logger.debug(s"Result of binding: ${result.values}")
          logger.debug(context2.toString)
          SeqToParams(result.values).asInstanceOf[I]
      }))

      val res: OptionT[F, F[Either[E, O]]] = maybeMatch.map(i => paramsAsArgs.applyFn(logic, i))

      res.flatMapF(_.map {
        case Right(result) =>
          Option(makeResponse(statusCodeToHttp4sStatus(statusMapper(result)), e.output, result))
        case Left(err) =>
          logger.error(err.toString)
          Option(makeResponse(statusCodeToHttp4sStatus(errorStatusMapper(err)), e.errorOutput, err))
      })
    }

    service
  }

  private def statusCodeToHttp4sStatus(code: tapir.StatusCode): Status = Status.fromInt(code).right.get

  private def createContext[I, E, O](e: Endpoint[I, E, O], req: Request[F]): F[Context[F]] = {
    val formAndBody: F[Option[Any]] = e.input.bodyType match {
      case None     => none[Any].pure[F]
      case Some(bt) => requestBody(req, bt).map(_.some)
    }

    formAndBody.map { body =>
      Context(
        queryParams = req.params,
        headers = req.headers,
        body = body,
        unmatchedPath = req.uri.renderString
      )
    }
  }

  private def requestBody[R](req: Request[F], rawBodyType: RawValueType[R]): F[R] = {
    def asChunk: F[Chunk[Byte]] = req.body.compile.toChunk
    def asByteArray: F[Array[Byte]] = req.body.compile.toChunk.map(_.toByteBuffer.array())

    rawBodyType match {
      case StringValueType(charset) => asByteArray.map(new String(_, req.charset.map(_.nioCharset).getOrElse(charset)))
      case ByteArrayValueType       => asByteArray
      case ByteBufferValueType      => asChunk.map(_.toByteBuffer)
      case InputStreamValueType     => asByteArray.map(new ByteArrayInputStream(_))
      case FileValueType =>
        serverOptions.createFile(serverOptions.blockingExecutionContext, req).flatMap { file =>
          val fileSink = fs2.io.file.writeAll(file.toPath, serverOptions.blockingExecutionContext)
          req.body.through(fileSink).compile.drain.map(_ => file)
        }
    }
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
}
