package tapir.server.http4s

import java.io.ByteArrayInputStream
import java.nio.charset.{Charset => NioCharset}

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
  DecodeResult,
  Endpoint,
  EndpointIO,
  EndpointInput,
  GeneralCodec,
  InputStreamValueType,
  MediaType,
  RawValueType,
  StringValueType
}

import scala.concurrent.ExecutionContext

class EndpointToHttp4sServer[F[_]: Sync: ContextShift](blockingExecutionContext: ExecutionContext) {

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

  private case class MatchResult(values: List[Any], ctx: Context) {
    def prependValue(v: Any): MatchResult = copy(values = v :: values)
  }

  private type Error = String

  private def mediaTypeToContentType(mediaType: MediaType): `Content-Type` =
    mediaType match {
      case MediaType.Json()             => `Content-Type`(http4s.MediaType.application.json)
      case MediaType.TextPlain(charset) => `Content-Type`(http4s.MediaType.text.plain, Charset.fromNioCharset(charset))
      case MediaType.OctetStream()      => `Content-Type`(http4s.MediaType.application.`octet-stream`)
    }

  private def encodeBody[T, M <: MediaType, R](v: T, codec: GeneralCodec[T, M, R]): Option[(EntityBody[F], Header)] = {
    val ct: `Content-Type` = mediaTypeToContentType(codec.mediaType)

    codec.encodeOptional(v).map { r: R =>
      codec.rawValueType.fold(r)(
        (s: String, c: NioCharset) => {
          val bytes = s.toString.getBytes(c)
          fs2.Stream.chunk(Chunk.bytes(bytes)) -> ct
        },
        b => fs2.Stream.chunk(Chunk.bytes(b)) -> ct,
        b => fs2.Stream.chunk(Chunk.byteBuffer(b)) -> ct,
        b => fs2.io.readInputStream(b.pure[F], 8192, blockingExecutionContext) -> ct
      )
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

  def toRoutes[I, E, O, FN[_]](e: Endpoint[I, E, O])(logic: FN[F[Either[E, O]]])(
      implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): HttpRoutes[F] = {

    val inputs: Vector[EndpointInput.Single[_]] = e.input.asVectorOfSingle
    logger.debug(s"Inputs: ")
    logger.debug(inputs.mkString("\n"))

    val service: HttpRoutes[F] = HttpRoutes[F] { req: Request[F] =>
      val context: F[Context] = (e.input.bodyType match {
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

      val response: ContextState = matchInputs(inputs)

      val methodMatches: Either[Error, String] =
        Either.cond(http4sMethodToTapirMethodMap
                      .get(req.method)
                      .contains(e.method),
                    "",
                    s"Method mismatch: got ${req.method}, expected: ${e.method}")

      val value: F[Either[Error, (Context, MatchResult)]] =
        EitherT
          .fromEither[F](methodMatches)
          .flatMap(_ =>
            EitherT(context
              .map(response.run)))
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
          Option(makeResponse(Status.Ok, e.output, result))
        case Left(err) =>
          logger.error(err.toString)
          None
      })
    }

    service
  }

  private def requestBody[R](req: Request[F], rawBodyType: RawValueType[R]): F[R] = {
    req.body.compile.toChunk.map { body =>
      def asByteArray = body.toByteBuffer.array()
      rawBodyType match {
        case StringValueType(charset) => new String(asByteArray, req.charset.map(_.nioCharset).getOrElse(charset))
        case ByteArrayValueType       => asByteArray
        case ByteBufferValueType      => body.toByteBuffer
        case InputStreamValueType     => new ByteArrayInputStream(asByteArray)
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

  private def nextSegment(unmatchedPath: String): Either[Error, (String, String)] =
    if (unmatchedPath.startsWith("/")) {
      val noSlash = unmatchedPath.drop(1)
      val lastIndex =
        if (noSlash.contains("/")) noSlash.indexOf("/")
        else if (noSlash.contains("?")) noSlash.indexOf("?")
        else
          noSlash.length
      val (segment, remaining) = noSlash.splitAt(lastIndex)
      Either.right((segment, remaining))
    } else {
      Either.left("path doesn't start with \"/\"")
    }

  private case class Context(queryParams: Map[String, String], headers: Headers, body: Option[Any], unmatchedPath: String) {
    def getHeader(key: String): Option[String] = headers.get(CaseInsensitiveString.apply(key)).map(_.value)
    def getQueryParam(name: String): Option[String] = queryParams.get(name)
    def dropPath(n: Int): Context = copy(unmatchedPath = unmatchedPath.drop(n))
  }

  private def handleMapped[II, T](wrapped: EndpointInput[II], f: II => T, inputsTail: Vector[EndpointInput.Single[_]]): ContextState =
    for {
      r1 <- matchInputs(wrapped.asVectorOfSingle)
      r2 <- matchInputs(inputsTail)
        .map(_.prependValue(f.asInstanceOf[Any => Any].apply(SeqToParams(r1.values))))
    } yield r2

  private def continueMatch(decodeResult: DecodeResult[Any], inputsTail: Vector[EndpointInput.Single[_]]): ContextState =
    decodeResult match {
      case DecodeResult.Value(v) =>
        logger.debug(s"Continuing match: $v")
        matchInputs(inputsTail).map(_.prependValue(v))
      case err =>
        StateT.inspectF((ctx: Context) => Either.left(s"${err.toString}, ${ctx.unmatchedPath}"))
    }

  private type ContextState = StateT[Either[Error, ?], Context, MatchResult]
  private def getState: StateT[Either[Error, ?], Context, Context] = StateT.get
  private def modifyState(f: Context => Context): StateT[Either[Error, ?], Context, Unit] =
    StateT.modify[Either[Error, ?], Context](f)

  private def matchInputs(inputs: Vector[EndpointInput.Single[_]]): ContextState = inputs match {
    case Vector() =>
      StateT(context => Either.right((context, MatchResult(Nil, context))))
    case EndpointInput.PathSegment(ss: String) +: inputsTail =>
      for {
        ctx <- getState
        _ <- modifyState(_.dropPath(ss.length + 1))
        doesMatch = ctx.unmatchedPath.drop(1).startsWith(ss)
        _ = logger.debug(s"$doesMatch, ${ctx.unmatchedPath}, $ss")
        r <- if (ctx.unmatchedPath.drop(1).startsWith(ss)) {
          val value: ContextState = matchInputs(inputsTail)
          value
        } else {
          val value: ContextState = StateT.liftF(Either.left(s"Unmatched path segment: $ss, $ctx"))
          value
        }
      } yield r
    case EndpointInput.PathCapture(m, name, _, _) +: inputsTail =>
      val decodeResult: StateT[Either[Error, ?], Context, DecodeResult[Any]] = StateT(
        (ctx: Context) =>
          nextSegment(ctx.unmatchedPath)
            .map {
              case (segment, remaining) =>
                logger.debug(s"Capturing path: $segment, remaining: $remaining, $name")
                (ctx.copy(unmatchedPath = remaining), m.decode(segment))
          })

      decodeResult.flatMap {
        case DecodeResult.Value(v) =>
          logger.debug(s"Decoded path: $v")
          matchInputs(inputsTail).map(_.prependValue(v))
        case decodingFailure => StateT.liftF(Either.left(s"Decoding path failed: $decodingFailure"))
      }
    case EndpointInput.Query(name, m, _, _) +: inputsTail =>
      for {
        ctx <- getState
        query = m.decodeOptional(ctx.getQueryParam(name))
        _ = logger.debug(s"Found query: $query, $name, ${ctx.headers}")
        res <- continueMatch(query, inputsTail)
      } yield res
    case EndpointIO.Header(name, m, _, _) +: inputsTail =>
      for {
        ctx <- getState
        header = m.decodeOptional(ctx.getHeader(name))
        _ = logger.debug(s"Found header: $header")
        res <- continueMatch(header, inputsTail)
      } yield res
    case EndpointIO.Body(codec, _, _) +: inputsTail =>
      for {
        ctx <- getState
        decoded: DecodeResult[Any] = {
          codec.decodeOptional(ctx.body)
        }
        res <- decoded match {
          case DecodeResult.Value(_) =>
            val r: ContextState = continueMatch(decoded, inputsTail)
            r
          case _ =>
            matchInputs(inputsTail)
        }
      } yield res
    case EndpointInput.Mapped(wrapped, f, _, _) +: inputsTail =>
      handleMapped(wrapped, f, inputsTail)
    case EndpointIO.Mapped(wrapped, f, _, _) +: inputsTail =>
      handleMapped(wrapped, f, inputsTail)
  }
}
