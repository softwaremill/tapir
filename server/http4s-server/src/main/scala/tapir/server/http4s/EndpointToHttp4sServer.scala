package tapir.server.http4s

import java.nio.charset.{Charset => NioCharset}

import cats.data._
import cats.effect.Sync
import cats.implicits._
import org.http4s
import org.http4s.headers.`Content-Type`
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Charset, EntityBody, Header, Headers, HttpRoutes, Request, Response, Status}
import tapir.internal.{ParamsToSeq, SeqToParams}
import tapir.typelevel.ParamsAsArgs
import tapir.{Endpoint, EndpointIO, EndpointInput, GeneralCodec, MediaType}

object EndpointToHttp4sServer {
  private val logger = org.log4s.getLogger

  private def mediaTypeToContentType(mediaType: MediaType): `Content-Type` =
    mediaType match {
      case MediaType.Json()             => `Content-Type`(http4s.MediaType.application.json)
      case MediaType.TextPlain(charset) => `Content-Type`(http4s.MediaType.text.plain, Charset.fromNioCharset(charset))
      case MediaType.OctetStream()      => `Content-Type`(http4s.MediaType.application.`octet-stream`)
    }

  private def encodeBody[T, M <: MediaType, R, F[_]: Sync](v: T, codec: GeneralCodec[T, M, R]): Option[(EntityBody[F], Header)] = {
    val ct: `Content-Type` = mediaTypeToContentType(codec.mediaType)
    codec.encodeOptional(v).map { r: R =>
      codec.rawValueType.fold(r)(
        (s: String, c: NioCharset) => {
          val byteIterator = s.toString.getBytes(c).iterator
          fs2.Stream.fromIterator[F, Byte](byteIterator) -> ct
        },
        b => fs2.Stream.fromIterator[F, Byte](b.toIterator) -> ct
      )
    }
  }

  private def singleOutputsWithValues[F[_]: Sync](outputs: Vector[EndpointIO.Single[_]],
                                                  v: Any): Vector[Either[(EntityBody[F], Header), Header]] = {
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
          singleOutputsWithValues[F](wrapped.asVectorOfSingle, g(vs(i)))
        res
    }
  }

  def toHttp4sRoutes[I, E, O, F[_]: Sync, FN[_]](e: Endpoint[I, E, O])(logic: FN[F[Either[E, O]]])(
      implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): HttpRoutes[F] = {

    val inputs: Vector[EndpointInput.Single[_]] = e.input.asVectorOfSingle
    logger.debug(s"Inputs: ")
    logger.debug(inputs.mkString("\n"))

    val service: HttpRoutes[F] = HttpRoutes[F] { req: Request[F] =>
      val isOctet: Boolean = req.contentType.exists(_.mediaType == org.http4s.MediaType.application.`octet-stream`)
      val byteBody: F[Option[Array[Byte]]] =
        req.body.compile.toList.map(bytes => if (bytes.isEmpty) None else Option(bytes.toArray))

      req.charset
      val context: F[Context[F]] =
        for {
          bytes <- byteBody
        } yield
          Context(
            queryParams = req.params,
            headers = req.headers,
            body =
              if (isOctet) bytes
              else
                bytes.map { bs =>
                  req.charset.fold(new String(bs))(c => new String(bs, c.nioCharset))
                },
            unmatchedPath = req.uri.renderString
          )

      val response: ContextState[F] = Http4sInputMatcher.matchInputs[F](inputs)

      val value: F[Either[Error, (Context[F], MatchResult[F])]] = context.map(response.run)

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
          Option(makeResponse(statusCodeToStatus(e.statusMapper(result)), e.output, result))
        case Left(err) =>
          logger.error(err.toString)
          Option(makeResponse(statusCodeToStatus(e.errorStatusMapper(err)), e.errorOutput, err))
      })
    }

    service
  }

  private def statusCodeToStatus(code: tapir.StatusCode): Status = {
    Status.fromInt(code) match {
      case Right(v) => v
      case _        => ???
    }
  }

  private def makeResponse[O, F[_]: Sync](statusCode: org.http4s.Status, output: EndpointIO[O], v: O): Response[F] = {
    val outputsWithValues: Vector[Either[(EntityBody[F], Header), Header]] =
      singleOutputsWithValues[F](output.asVectorOfSingle, v)

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
