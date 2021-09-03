package sttp.tapir.static

import sttp.model.headers.ETag
import sttp.model.{Header, HeaderNames, MediaType, StatusCode}
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.internal.TapirFile
import sttp.tapir.server.ServerEndpoint

import java.io.InputStream
import java.time.Instant

/** Static content endpoints, including files and resources. */
trait TapirStaticContentEndpoints {
  // we can't use oneOfMapping and mapTo since they are macros, defined in the same compilation unit

  private val pathsWithoutDots: EndpointInput[List[String]] =
    paths.mapDecode(ps =>
      if (ps.exists(p => p == "" || p == "." || p == ".."))
        DecodeResult.Error(ps.mkString("/"), new RuntimeException(s"Incorrect path: $ps"))
      else DecodeResult.Value(ps)
    )(identity)

  private val ifNoneMatchHeader: EndpointIO[Option[List[ETag]]] =
    header[Option[String]](HeaderNames.IfNoneMatch).mapDecode[Option[List[ETag]]] {
      case None    => DecodeResult.Value(None)
      case Some(h) => DecodeResult.fromEitherString(h, ETag.parseList(h)).map(Some(_))
    }(_.map(es => ETag.toString(es)))

  private def optionalHttpDateHeader(headerName: String): EndpointIO[Option[Instant]] =
    header[Option[String]](headerName).mapDecode[Option[Instant]] {
      case None    => DecodeResult.Value(None)
      case Some(v) => DecodeResult.fromEitherString(v, Header.parseHttpDate(v)).map(Some(_))
    }(_.map(Header.toHttpDateString))

  private val ifModifiedSinceHeader: EndpointIO[Option[Instant]] = optionalHttpDateHeader(HeaderNames.IfModifiedSince)

  private val lastModifiedHeader: EndpointIO[Option[Instant]] = optionalHttpDateHeader(HeaderNames.LastModified)

  private val contentTypeHeader: EndpointIO[Option[MediaType]] = header[Option[String]](HeaderNames.ContentType).mapDecode {
    case None    => DecodeResult.Value(None)
    case Some(v) => DecodeResult.fromEitherString(v, MediaType.parse(v)).map(Some(_))
  }(_.map(_.toString))

  private val etagHeader: EndpointIO[Option[ETag]] = header[Option[String]](HeaderNames.Etag).mapDecode[Option[ETag]] {
    case None    => DecodeResult.Value(None)
    case Some(v) => DecodeResult.fromEitherString(v, ETag.parse(v)).map(Some(_))
  }(_.map(_.toString))

  private val rangeHeader: EndpointIO[Option[RangeValue]] = header[Option[String]](HeaderNames.Range).mapDecode[Option[RangeValue]] {
    case None    => DecodeResult.Value(None)
    case Some(v) => DecodeResult.fromEitherString(v, RangeValue.fromString(v).map(Some(_)))
  }(_.map(_.toString))

  private def staticEndpoint[T](
      prefix: EndpointInput[Unit],
      body: EndpointOutput[T]
  ): Endpoint[StaticInput, StaticErrorOutput, StaticOutput[T], Any] = {
    endpoint.get
      .in(prefix)
      .in(
        pathsWithoutDots
          .and(ifNoneMatchHeader)
          .and(ifModifiedSinceHeader)
          .map[StaticInput]((t: (List[String], Option[List[ETag]], Option[Instant])) => StaticInput(t._1, t._2, t._3))(fi =>
            (fi.path, fi.ifNoneMatch, fi.ifModifiedSince)
          )
      )
      .errorOut(
        oneOf[StaticErrorOutput](
          oneOfMappingClassMatcher(
            StatusCode.NotFound,
            emptyOutputAs(StaticErrorOutput.NotFound),
            StaticErrorOutput.NotFound.getClass
          ),
          oneOfMappingClassMatcher(
            StatusCode.BadRequest,
            emptyOutputAs(StaticErrorOutput.BadRequest),
            StaticErrorOutput.BadRequest.getClass
          )
        )
      )
      .out(
        oneOf[StaticOutput[T]](
          oneOfMappingClassMatcher(StatusCode.NotModified, emptyOutputAs(StaticOutput.NotModified), StaticOutput.NotModified.getClass),
          oneOfMappingClassMatcher(
            StatusCode.Ok,
            body
              .and(lastModifiedHeader)
              .and(header[Option[Long]](HeaderNames.ContentLength))
              .and(contentTypeHeader)
              .and(etagHeader)
              .map[StaticOutput.Found[T]]((t: (T, Option[Instant], Option[Long], Option[MediaType], Option[ETag])) =>
                StaticOutput.Found(t._1, t._2, t._3, t._4, t._5, Some(""), Some("a"))
              )(fo => (fo.body, fo.lastModified, fo.contentLength, fo.contentType, fo.etag)),
            classOf[StaticOutput.Found[T]]
          )
        )
      )
  }

  private def staticEndpointRanged[T](
    prefix: EndpointInput[Unit],
    body: EndpointOutput[T]
  ): Endpoint[StaticInput, StaticErrorOutput, StaticOutput[T], Any] = {
    endpoint.get
      .in(prefix)
      .in(
        pathsWithoutDots
          .and(ifNoneMatchHeader)
          .and(ifModifiedSinceHeader)
          .and(rangeHeader)
          .map[StaticInput]((t: (List[String], Option[List[ETag]], Option[Instant], Option[RangeValue])) => StaticInput(t._1, t._2, t._3, t._4))(fi =>
            (fi.path, fi.ifNoneMatch, fi.ifModifiedSince, fi.range)
          )
      )
      .errorOut(
        oneOf[StaticErrorOutput](
          oneOfMappingClassMatcher(
            StatusCode.NotFound,
            emptyOutputAs(StaticErrorOutput.NotFound),
            StaticErrorOutput.NotFound.getClass
          ),
          oneOfMappingClassMatcher(
            StatusCode.BadRequest,
            emptyOutputAs(StaticErrorOutput.BadRequest),
            StaticErrorOutput.BadRequest.getClass
          )
          ,
          oneOfMappingClassMatcher(
            StatusCode.RangeNotSatisfiable,
            emptyOutputAs(StaticErrorOutput.RangeNotSatisfiable),
            StaticErrorOutput.RangeNotSatisfiable.getClass
          )
        )
      )
      .out(
        oneOf[StaticOutput[T]](
          oneOfMappingClassMatcher(StatusCode.NotModified, emptyOutputAs(StaticOutput.NotModified), StaticOutput.NotModified.getClass),
          oneOfMappingClassMatcher(
            StatusCode.Ok,
            body
              .and(lastModifiedHeader)
              .and(header[Option[Long]](HeaderNames.ContentLength))
              .and(contentTypeHeader)
              .and(etagHeader)
              .and(header[Option[String]](HeaderNames.AcceptRanges))
              .and(header[Option[String]](HeaderNames.ContentRange))
              .map[StaticOutput.Found[T]]((t: (T, Option[Instant], Option[Long], Option[MediaType], Option[ETag], Option[String], Option[String])) =>
                StaticOutput.Found(t._1, t._2, t._3, t._4, t._5, t._6, t._7)
              )(fo => (fo.body, fo.lastModified, fo.contentLength, fo.contentType, fo.etag, fo.acceptRanges, fo.contentRange)),
            classOf[StaticOutput.Found[T]]
          )
        )
      )
  }

  def filesEndpoint(prefix: EndpointInput[Unit]): Endpoint[StaticInput, StaticErrorOutput, StaticOutput[TapirFile], Any] =
    staticEndpoint(prefix, tapirFileBody)

  def filesEndpointRanged(prefix: EndpointInput[Unit]): Endpoint[StaticInput, StaticErrorOutput, StaticOutput[TapirFile], Any] =
    staticEndpointRanged(prefix, tapirFileBody)

  def resourcesEndpoint(prefix: EndpointInput[Unit]): Endpoint[StaticInput, StaticErrorOutput, StaticOutput[InputStream], Any] =
    staticEndpoint(prefix, inputStreamBody)

  /** A server endpoint, which exposes files from local storage found at `systemPath`, using the given `prefix`. Typically, the prefix is a
    * path, but it can also contain other inputs. For example:
    *
    * {{{
    * filesServerEndpoint("static" / "files")("/home/app/static")
    * }}}
    *
    * A request to `/static/files/css/styles.css` will try to read the `/home/app/static/css/styles.css` file.
    */
  def filesServerEndpoint[F[_]](prefix: EndpointInput[Unit])(
      systemPath: String
  ): ServerEndpoint[StaticInput, StaticErrorOutput, StaticOutput[TapirFile], Any, F] =
    ServerEndpoint(filesEndpoint(prefix), (m: MonadError[F]) => Files(systemPath)(m))

  def filesServerEndpointRanged[F[_]](prefix: EndpointInput[Unit])(
    systemPath: String
  ): ServerEndpoint[StaticInput, StaticErrorOutput, StaticOutput[TapirFile], Any, F] =
    ServerEndpoint(filesEndpointRanged(prefix), (m: MonadError[F]) => Files(systemPath)(m))

  /** A server endpoint, which exposes a single file from local storage found at `systemPath`, using the given `path`.
    *
    * {{{
    * fileServerEndpoint("static" / "hello.html")("/home/app/static/data.html")
    * }}}
    */
  def fileServerEndpoint[F[_]](path: EndpointInput[Unit])(
      systemPath: String
  ): ServerEndpoint[StaticInput, StaticErrorOutput, StaticOutput[TapirFile], Any, F] =
    ServerEndpoint(removePath(filesEndpoint(path)), (m: MonadError[F]) => Files(systemPath)(m))

  /** A server endpoint, which exposes resources available from the given `classLoader`, using the given `prefix`. Typically, the prefix is
    * a path, but it can also contain other inputs. For example:
    *
    * {{{
    * resourcesServerEndpoint("static" / "files")(classOf[App].getClassLoader, "app")
    * }}}
    *
    * A request to `/static/files/css/styles.css` will try to read the `/app/css/styles.css` resource.
    */
  def resourcesServerEndpoint[F[_]](prefix: EndpointInput[Unit])(
      classLoader: ClassLoader,
      resourcePrefix: String
  ): ServerEndpoint[StaticInput, StaticErrorOutput, StaticOutput[InputStream], Any, F] =
    ServerEndpoint(resourcesEndpoint(prefix), (m: MonadError[F]) => Resources(classLoader, resourcePrefix)(m))

  /** A server endpoint, which exposes a single resource available from the given `classLoader` at `resourcePath`, using the given `path`.
    *
    * {{{
    * resourceServerEndpoint("static" / "hello.html")(classOf[App].getClassLoader, "app/data.html")
    * }}}
    */
  def resourceServerEndpoint[F[_]](prefix: EndpointInput[Unit])(
      classLoader: ClassLoader,
      resourcePath: String
  ): ServerEndpoint[StaticInput, StaticErrorOutput, StaticOutput[InputStream], Any, F] =
    ServerEndpoint(removePath(resourcesEndpoint(prefix)), (m: MonadError[F]) => Resources(classLoader, resourcePath)(m))

  private def removePath[T](e: Endpoint[StaticInput, StaticErrorOutput, StaticOutput[T], Any]) =
    e.mapIn(i => i.copy(path = Nil))(i => i.copy(path = Nil))
}
