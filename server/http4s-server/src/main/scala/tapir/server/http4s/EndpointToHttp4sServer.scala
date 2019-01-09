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
      case MediaType.Json()             => `Content-Type`(http4s.MediaType.application.json)
      case MediaType.TextPlain(charset) => `Content-Type`(http4s.MediaType.text.plain, Charset.fromNioCharset(charset))
      case MediaType.OctetStream()      => `Content-Type`(http4s.MediaType.application.`octet-stream`)
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

  private def singleOutputsWithValues(outputs: Vector[EndpointIO.Single[_]], v: Any): Vector[Either[(EntityBody[F], Header), Header]] = {
    val vs = ParamsToSeq(v)

    outputs.zipWithIndex.flatMap {
      case (EndpointIO.Body(codec, _, _), i) =>
        val maybeTuple: Option[(EntityBody[F], Header)] = encodeBody(vs(i), codec)
        maybeTuple.toVector.map(Either.left)

      case (EndpointIO.Header(name, codec, _, _), i) =>
        Vector(
          codec
            .encodeOptional(vs(i))
            .map((headerValue: String) => Header.Raw(CaseInsensitiveString(name), headerValue))
            .toRight(???))

      case (EndpointIO.Mapped(wrapped, _, g, _), i) =>
        val res: Vector[Either[(EntityBody[F], Header), Header]] =
          singleOutputsWithValues(wrapped.asVectorOfSingle, g(vs(i)))
        res
    }
  }

  def toRoutes[I, E, O, FN[_]](e: Endpoint[I, E, O])(
      logic: FN[F[Either[E, O]]],
      statusMapper: O => StatusCode,
      errorStatusMapper: E => StatusCode)(implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): HttpRoutes[F] = {

    val inputs: Vector[EndpointInput.Single[_]] = e.input.asVectorOfSingle
    logger.debug(s"Inputs: ")
    logger.debug(inputs.mkString("\n"))

    val service: HttpRoutes[F] = HttpRoutes[F] { req: Request[F] =>
      val context: F[Context[F]] = (e.input.bodyType match {
        case None     => None.pure[F]
        case Some(bt) => requestBody(req, bt).map(_.some)
      }).map { body =>
        Context(
          queryParams = req.params,
          headers = req.headers,
          body = body,
          unmatchedPath = req.uri.renderString
        )
      }

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
    val outputsWithValues: Vector[Either[(EntityBody[F], Header), Header]] =
      singleOutputsWithValues(output.asVectorOfSingle, v)

    val bodyOpt: Option[(EntityBody[F], Header)] = outputsWithValues.collectFirst {
      case Left((b, contentType)) => (b, contentType)
    }
    val responseHeaders = outputsWithValues.collect { case Right(h) => h }.toList

    bodyOpt match {
      case Some((body, contentType)) =>
        val parsedHeaders = Headers(contentType :: responseHeaders)
        Response(status = statusCode, headers = parsedHeaders, body = body)
      case None =>
        Response(status = statusCode, headers = Headers(responseHeaders))
    }

  }
}
