package tapir.server.http4s

import java.nio.charset.StandardCharsets.UTF_8

import cats.Applicative
import cats.data._
import cats.effect.Sync
import cats.implicits._
import org.http4s
import org.http4s.headers.`Content-Type`
import org.http4s.parser.HttpHeaderParser
import org.http4s.util.CaseInsensitiveString
import org.http4s.{EntityBody, Header, Headers, HttpRoutes, ParseResult, Request, Response, Status}
import tapir.internal.{ParamsToSeq, SeqToParams}
import tapir.typelevel.ParamsAsArgs
import tapir.{DecodeResult, Endpoint, EndpointIO, EndpointInput, MediaType}

import scala.language.higherKinds

trait Http4sServer {

  implicit class RichHttp4sHttpEndpoint[I, E, O](e: Endpoint[I, E, O]) {

    private val logger = org.log4s.getLogger

    case class MatchResult[F[_]](values: List[Any], ctx: Context[F]) {
      def prependValue(v: Any): MatchResult[F] = copy(values = v :: values)
    }

    type Error = String

    private def mediaTypeToContentType(mediaType: MediaType): `Content-Type` =
      mediaType match {
        case MediaType.Json()        => `Content-Type`(http4s.MediaType.application.json)
        case MediaType.TextPlain()   => `Content-Type`(http4s.MediaType.text.plain)
        case MediaType.OctetStream() => `Content-Type`(http4s.MediaType.application.`octet-stream`)
      }

    private def singleOutputsWithValues[F[_]: Sync](outputs: Vector[EndpointIO.Single[_]],
                                                    v: Any): Vector[Either[(EntityBody[F], Header), Header]] = {
      val vs = ParamsToSeq(v)

      outputs.zipWithIndex.flatMap {
        case (EndpointIO.Body(m, _, _), i) =>
          val res: Vector[Either[(EntityBody[F], Header), Header]] = Vector(
            m.decodeOptional(Option(vs(i)))
              .map { b =>
                val byteIterator = b.toString.getBytes(UTF_8).iterator // TODO: Do something sensible
                Left((fs2.Stream.fromIterator[F, Byte](byteIterator), mediaTypeToContentType(m.mediaType)))
              }
              .getOrThrow((_, maybeT) => maybeT.get))
          res
        case (EndpointIO.Header(name, m, _, _), i) =>
          val value: ParseResult[Header] = m
            .decodeOptional(Option(vs(i).toString))
            .map { value =>
              HttpHeaderParser.parseHeader(Header.Raw(CaseInsensitiveString(name), value.toString))
            }
            .getOrThrow((_, maybeT) => maybeT.get)
          val res: Vector[Either[(EntityBody[F], Header), Header]] = Vector(value match {
            case Right(header) => Either.right(header)
            case _             => ???
          })
          res
        case (EndpointIO.Mapped(wrapped, _, g, _), i) =>
          val res: Vector[Either[(EntityBody[F], Header), Header]] = singleOutputsWithValues[F](wrapped.asVectorOfSingle, g(vs(i)))
          res
      }
    }

    def toHttp4sService[F[_]: Sync, FN[_]](logic: FN[F[Either[E, O]]])(implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): HttpRoutes[F] = {

      val inputs = e.input.asVectorOfSingle

      logger.debug(s"Inputs: ")
      logger.debug(inputs.mkString("\n"))

      val service: HttpRoutes[F] = HttpRoutes[F] { req: Request[F] =>
        val context: F[Context[F]] =
          req.bodyAsText.compile.last.map(maybeBody =>
            Context[F](queryParams = req.params, headers = req.headers, body = maybeBody, unmatchedPath = req.uri.renderString))

        val response: ContextState[F] = matchInputs[F](inputs)

        val value: F[Either[Error, (Context[F], MatchResult[F])]] = context.map(response.run)

        logger.debug(s"Result of binding: ${value}")

        val maybeMatch: OptionT[F, I] = OptionT(value.map(_.toOption.map {
          case (context, result) =>
            logger.debug(s"Result of binding: ${result.values}")
            logger.debug(context.toString)
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

    private def makeResponse[F[_]: Sync](statusCode: org.http4s.Status, output: EndpointIO[O], v: O): Response[F] = {
      val outputsWithValues: Vector[Either[(EntityBody[F], Header), Header]] =
        singleOutputsWithValues[F](output.asVectorOfSingle, v)

      val body: Option[(EntityBody[F], Header)] = outputsWithValues.collectFirst {
        case Left((b, contentType)) => (b, contentType)
      }
      val responseHeaders = outputsWithValues.collect { case Right(h) => h }.toList

      body match {
        case Some((body, contentType)) =>
          val parsedHeaders = Headers(contentType :: responseHeaders)
          Response(status = statusCode, headers = parsedHeaders, body = body)
        case None =>
          Response(status = statusCode, headers = Headers(responseHeaders))
      }

    }

    def nextSegment(unmatchedPath: String): Either[Error, (String, String)] =
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

    case class Context[F[_]](queryParams: Map[String, String], headers: Headers, body: Option[String], unmatchedPath: String) {
      def getHeader(key: String): Option[String] = headers.get(CaseInsensitiveString.apply(key)).map(_.value)
      def getQueryParam(name: String): Option[String] = queryParams.get(name)
      def dropPath(n: Int): Context[F] = copy(unmatchedPath = unmatchedPath.drop(n))
    }

    def handleMapped[II, T, F[_]: Sync](wrapped: EndpointInput[II],
                                        f: II => T,
                                        inputsTail: Vector[EndpointInput.Single[_]]): ContextState[F] =
      for {
        r1 <- matchInputs[F](wrapped.asVectorOfSingle)
        r2 <- matchInputs[F](inputsTail)
          .map(_.prependValue(f.asInstanceOf[Any => Any].apply(SeqToParams(r1.values))))
      } yield r2

    private def continueMatch[F[_]: Sync](decodeResult: DecodeResult[Any], inputsTail: Vector[EndpointInput.Single[_]]): ContextState[F] =
      decodeResult match {
        case DecodeResult.Value(v) =>
          logger.debug(s"Continuing match: ${v}")
          matchInputs[F](inputsTail).map(_.prependValue(v))
        case err =>
          StateT.inspectF((ctx: Context[F]) => Either.left(s"${err.toString}, ${ctx.unmatchedPath}"))
      }

    type ContextState[F[_]] = StateT[Either[Error, ?], Context[F], MatchResult[F]]
    private def getState[F[_]]: StateT[Either[Error, ?], Context[F], Context[F]] = StateT.get
    private def modifyState[F[_]: Applicative](f: Context[F] => Context[F]): StateT[Either[Error, ?], Context[F], Unit] =
      StateT.modify[Either[Error, ?], Context[F]](f)

    def matchInputs[F[_]: Sync](inputs: Vector[EndpointInput.Single[_]]): ContextState[F] = inputs match {
      case Vector() =>
        StateT(context => Either.right((context, MatchResult[F](Nil, context))))
      case EndpointInput.PathSegment(ss: String) +: inputsTail =>
        for {
          ctx <- getState[F]
          _ <- modifyState[F](_.dropPath(ss.length + 1))
          doesMatch = ctx.unmatchedPath.drop(1).startsWith(ss)
          _ = logger.debug(s"${doesMatch}, ${ctx.unmatchedPath}, ${ss}")
          r <- if (ctx.unmatchedPath.drop(1).startsWith(ss)) {
            val value: ContextState[F] = matchInputs[F](inputsTail)
            value
          } else {
            val value: ContextState[F] = StateT.liftF(Either.left(s"Unmatched path segment: ${ss}, ${ctx}"))
            value
          }
        } yield r
      case EndpointInput.PathCapture(m, name, _, _) +: inputsTail =>
        val decodeResult: StateT[Either[Error, ?], Context[F], DecodeResult[Any]] = StateT(
          (ctx: Context[F]) =>
            nextSegment(ctx.unmatchedPath)
              .map {
                case (segment, remaining) =>
                  logger.debug(s"Capturing path: ${segment}, remaining: ${remaining}, ${name}")
                  (ctx.copy(unmatchedPath = remaining), m.decode(segment))
            })

        decodeResult.flatMap {
          case DecodeResult.Value(v) =>
            logger.debug(s"Decoded path: ${v}")
            matchInputs[F](inputsTail).map(_.prependValue(v))
          case decodingFailure => StateT.liftF(Either.left(s"Decoding path failed: $decodingFailure"))
        }
      case EndpointInput.Query(name, m, _, _) +: inputsTail =>
        for {
          ctx <- getState[F]
          query = m.decodeOptional(ctx.getQueryParam(name))
          _ = logger.debug(s"Found query: ${query}, ${name}, ${ctx.headers}")
          res <- continueMatch(query, inputsTail)
        } yield res
      case EndpointIO.Header(name, m, _, _) +: inputsTail =>
        for {
          ctx <- getState[F]
          header = m.decodeOptional(ctx.getHeader(name))
          _ = logger.debug(s"Found header: ${header}")
          res <- continueMatch(header, inputsTail)
        } yield res
      case EndpointIO.Body(m, _, _) +: inputsTail =>
        for {
          ctx <- getState[F]
          res <- continueMatch(m.decodeOptional(ctx.body), inputsTail)
        } yield res
      case EndpointInput.Mapped(wrapped, f, _, _) +: inputsTail =>
        handleMapped(wrapped, f, inputsTail)
      case EndpointIO.Mapped(wrapped, f, _, _) +: inputsTail =>
        handleMapped(wrapped, f, inputsTail)
    }

  }

}
