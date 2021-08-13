package sttp.tapir.files

import sttp.model.{Header, HeaderNames, StatusCode}
import sttp.model.headers.ETag
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint

import java.io.{File, InputStream}
import java.time.Instant

class TapirFilesEndpoints {
  // we can't use oneOfMapping and mapTo since they are macros, defined in the same compilation unit

  private val pathsWithoutDots: EndpointInput[List[String]] =
    paths.mapDecode(ps =>
      if (ps.exists(p => p == "|" || p == "." || p == ".."))
        DecodeResult.Error(ps.mkString("/"), new RuntimeException(s"Incorrect path: $ps"))
      else DecodeResult.Value(ps)
    )(identity)

  private val ifNoneMatchHeader: EndpointIO[Option[List[ETag]]] =
    header[Option[String]](HeaderNames.IfNoneMatch).mapDecode[Option[List[ETag]]] {
      case None => DecodeResult.Value(None)
      case Some(h) =>
        ETag.parseList(h) match {
          case Left(e)   => DecodeResult.Error(h, new RuntimeException(e))
          case Right(es) => DecodeResult.Value(Some(es))
        }
    }(_.map(es => ETag.toString(es)))

  private def optionalHttpDateHeader(headerName: String): EndpointIO[Option[Instant]] =
    header[Option[String]](headerName).mapDecode[Option[Instant]] {
      case None => DecodeResult.Value(None)
      case Some(v) =>
        Header.parseHttpDate(v) match {
          case Left(e)  => DecodeResult.Error(v, new RuntimeException(e))
          case Right(i) => DecodeResult.Value(Some(i))
        }
    }(_.map(Header.toHttpDateString))

  private val ifModifiedSinceHeader: EndpointIO[Option[Instant]] = optionalHttpDateHeader(HeaderNames.IfModifiedSince)

  private val lastModifiedHeader: EndpointIO[Option[Instant]] = optionalHttpDateHeader(HeaderNames.LastModified)

  private val etagHeader: EndpointIO[Option[ETag]] = header[Option[String]](HeaderNames.Etag).mapDecode[Option[ETag]] {
    case None => DecodeResult.Value(None)
    case Some(v) =>
      ETag.parse(v) match {
        case Left(e)  => DecodeResult.Error(v, new RuntimeException(e))
        case Right(e) => DecodeResult.Value(Some(e))
      }
  }(_.map(_.toString))

  private val eitherFileOrInputStream = oneOf[Either[File, InputStream]](
    oneOfMappingClassMatcher(StatusCode.Ok, fileBody.map(Left(_))(_.value), classOf[Left[File, InputStream]]),
    oneOfMappingClassMatcher(StatusCode.Ok, inputStreamBody.map(Right(_))(_.value), classOf[Right[File, InputStream]])
  )

  def filesEndpoint(prefix: EndpointInput[Unit]): Endpoint[FilesInput, FilesErrorOutput, FilesOutput, Any] = {
    endpoint.get
      .in(prefix)
      .in(
        pathsWithoutDots
          .and(ifNoneMatchHeader)
          .and(ifModifiedSinceHeader)
          .map[FilesInput]((t: (List[String], Option[List[ETag]], Option[Instant])) => FilesInput(t._1, t._2, t._3))(fi =>
            (fi.path, fi.ifNoneMatch, fi.ifModifiedSince)
          )
      )
      .errorOut(
        oneOf[FilesErrorOutput](
          oneOfMappingClassMatcher(
            StatusCode.NotFound,
            emptyOutputAs(FilesErrorOutput.NotFound),
            FilesErrorOutput.NotFound.getClass
          ),
          oneOfMappingClassMatcher(
            StatusCode.BadRequest,
            emptyOutputAs(FilesErrorOutput.BadRequest),
            FilesErrorOutput.BadRequest.getClass
          )
        )
      )
      .out(
        oneOf[FilesOutput](
          oneOfMappingClassMatcher(StatusCode.NotModified, emptyOutputAs(FilesOutput.NotModified), FilesOutput.NotModified.getClass),
          oneOfMappingClassMatcher(
            StatusCode.Ok,
            eitherFileOrInputStream
              .and(lastModifiedHeader)
              .and(header[Option[Long]](HeaderNames.ContentLength))
              .and(header[Option[String]](HeaderNames.ContentType))
              .and(etagHeader)
              .map[FilesOutput.Found]((t: (Either[File, InputStream], Option[Instant], Option[Long], Option[String], Option[ETag])) =>
                FilesOutput.Found(t._1, t._2, t._3, t._4, t._5)
              )(fo => (fo.body, fo.lastModified, fo.contentLength, fo.contentType, fo.etag)),
            classOf[FilesOutput.Found]
          )
        )
      )
  }

  //def filesServerEndpoint[F[_]](prefix: EndpointInput[Unit]): ServerEndpoint[FilesInput, FilesErrorOutput, FilesOutput, Any, F] = ???

  // logic params: base path
}
