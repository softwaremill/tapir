package sttp.tapir.static

import sttp.model.headers.{ETag, Range}
import sttp.model.{Header, HeaderNames, MediaType, StatusCode}
import sttp.monad.MonadError
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.{FileRange, _}

import java.io.{File, InputStream}
import java.time.Instant

/** Static content endpoints, including files and resources. */
trait TapirStaticContentEndpoints {
  // we can't use oneOfVariant and mapTo since they are macros, defined in the same compilation unit

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
  private val contentTypeHeader: EndpointIO[Option[MediaType]] = header[Option[MediaType]](HeaderNames.ContentType)
  private val etagHeader: EndpointIO[Option[ETag]] = header[Option[ETag]](HeaderNames.Etag)
  private val rangeHeader: EndpointIO[Option[Range]] = header[Option[Range]](HeaderNames.Range)
  private val acceptEncodingHeader: EndpointIO[Option[String]] = header[Option[String]](HeaderNames.AcceptEncoding)
  private val contentEncodingHeader: EndpointIO[Option[String]] = header[Option[String]](HeaderNames.ContentEncoding)

  private def staticGetEndpoint[T](
      prefix: EndpointInput[Unit],
      body: EndpointOutput[T]
  ): PublicEndpoint[StaticInput, StaticErrorOutput, StaticOutput[T], Any] = {
    endpoint.get
      .in(prefix)
      .in(
        pathsWithoutDots
          .and(ifNoneMatchHeader)
          .and(ifModifiedSinceHeader)
          .and(rangeHeader)
          .and(acceptEncodingHeader)
          .map[StaticInput]((t: (List[String], Option[List[ETag]], Option[Instant], Option[Range], Option[String])) =>
            StaticInput(t._1, t._2, t._3, t._4, t._5)
          )(fi => (fi.path, fi.ifNoneMatch, fi.ifModifiedSince, fi.range, fi.acceptEncoding))
      )
      .errorOut(
        oneOf[StaticErrorOutput](
          oneOfVariantClassMatcher(
            StatusCode.NotFound,
            emptyOutputAs(StaticErrorOutput.NotFound),
            StaticErrorOutput.NotFound.getClass
          ),
          oneOfVariantClassMatcher(
            StatusCode.BadRequest,
            emptyOutputAs(StaticErrorOutput.BadRequest),
            StaticErrorOutput.BadRequest.getClass
          ),
          oneOfVariantClassMatcher(
            StatusCode.RangeNotSatisfiable,
            emptyOutputAs(StaticErrorOutput.RangeNotSatisfiable),
            StaticErrorOutput.RangeNotSatisfiable.getClass
          )
        )
      )
      .out(
        oneOf[StaticOutput[T]](
          oneOfVariantClassMatcher(StatusCode.NotModified, emptyOutputAs(StaticOutput.NotModified), StaticOutput.NotModified.getClass),
          oneOfVariantClassMatcher(
            StatusCode.PartialContent,
            body
              .and(lastModifiedHeader)
              .and(header[Option[Long]](HeaderNames.ContentLength))
              .and(contentTypeHeader)
              .and(etagHeader)
              .and(header[Option[String]](HeaderNames.AcceptRanges))
              .and(header[Option[String]](HeaderNames.ContentRange))
              .map[StaticOutput.FoundPartial[T]](
                (t: (T, Option[Instant], Option[Long], Option[MediaType], Option[ETag], Option[String], Option[String])) =>
                  StaticOutput.FoundPartial(t._1, t._2, t._3, t._4, t._5, t._6, t._7)
              )(fo => (fo.body, fo.lastModified, fo.contentLength, fo.contentType, fo.etag, fo.acceptRanges, fo.contentRange)),
            classOf[StaticOutput.FoundPartial[T]]
          ),
          oneOfVariantClassMatcher(
            StatusCode.Ok,
            body
              .and(lastModifiedHeader)
              .and(header[Option[Long]](HeaderNames.ContentLength))
              .and(contentTypeHeader)
              .and(etagHeader)
              .and(contentEncodingHeader)
              .map[StaticOutput.Found[T]]((t: (T, Option[Instant], Option[Long], Option[MediaType], Option[ETag], Option[String])) =>
                StaticOutput.Found(t._1, t._2, t._3, t._4, t._5, t._6)
              )(fo => (fo.body, fo.lastModified, fo.contentLength, fo.contentType, fo.etag, fo.contentEncoding)),
            classOf[StaticOutput.Found[T]]
          )
        )
      )
  }

  private def staticHeadEndpoint(
      prefix: EndpointInput[Unit]
  ): PublicEndpoint[HeadInput, StaticErrorOutput, HeadOutput, Any] = {
    endpoint.head
      .in(prefix)
      .in(pathsWithoutDots.map[HeadInput](t => HeadInput(t))(_.path))
      .errorOut(
        oneOf[StaticErrorOutput](
          oneOfVariantClassMatcher(
            StatusCode.BadRequest,
            emptyOutputAs(StaticErrorOutput.BadRequest),
            StaticErrorOutput.BadRequest.getClass
          ),
          oneOfVariantClassMatcher(
            StatusCode.NotFound,
            emptyOutputAs(StaticErrorOutput.NotFound),
            StaticErrorOutput.NotFound.getClass
          )
        )
      )
      .out(
        oneOf[HeadOutput](
          oneOfVariantClassMatcher(
            StatusCode.Ok,
            header[Option[String]](HeaderNames.AcceptRanges)
              .and(header[Option[Long]](HeaderNames.ContentLength))
              .and(contentTypeHeader)
              .map[HeadOutput.Found]((t: (Option[String], Option[Long], Option[MediaType])) => HeadOutput.Found(t._1, t._2, t._3))(fo =>
                (fo.acceptRanges, fo.contentLength, fo.contentType)
              ),
            classOf[HeadOutput.Found]
          )
        )
      )
  }

  def filesGetEndpoint(prefix: EndpointInput[Unit]): PublicEndpoint[StaticInput, StaticErrorOutput, StaticOutput[FileRange], Any] =
    staticGetEndpoint(prefix, fileRangeBody)

  def resourcesGetEndpoint(prefix: EndpointInput[Unit]): PublicEndpoint[StaticInput, StaticErrorOutput, StaticOutput[InputStream], Any] =
    staticGetEndpoint(prefix, inputStreamBody)

  /** A server endpoint, which exposes files from local storage found at `systemPath`, using the given `prefix`. Typically, the prefix is a
    * path, but it can also contain other inputs. For example:
    *
    * {{{
    * filesGetServerEndpoint("static" / "files")("/home/app/static")
    * }}}
    *
    * A request to `/static/files/css/styles.css` will try to read the `/home/app/static/css/styles.css` file.
    *
    * @param fileFilter
    *   file will exposed only if this function returns `true`.
    */
  def filesGetServerEndpoint[F[_]](
      prefix: EndpointInput[Unit]
  )(systemPath: String, fileFilter: List[String] => Boolean = _ => true): ServerEndpoint[Any, F] =
    ServerEndpoint.public(
      filesGetEndpoint(prefix),
      (m: MonadError[F]) => Files.get(systemPath, Files.defaultEtag[F](_: File)(m), fileFilter)(m)
    )

  /** A server endpoint, used to verify if sever supports range requests for file under particular path Additionally it verify file
    * existence and returns its size
    *
    * @param fileFilter
    *   file will exposed only if this function returns `true`.
    */
  def filesHeadServerEndpoint[F[_]](
      prefix: EndpointInput[Unit]
  )(systemPath: String, fileFilter: List[String] => Boolean = _ => true): ServerEndpoint[Any, F] =
    ServerEndpoint.public(staticHeadEndpoint(prefix), (m: MonadError[F]) => Files.head(systemPath, fileFilter)(m))

  /** Create a pair of endpoints (head, get) for exposing files from local storage found at `systemPath`, using the given `prefix`.
    * Typically, the prefix is a path, but it can also contain other inputs. For example:
    *
    * {{{
    * filesServerEndpoints("static" / "files")("/home/app/static")
    * }}}
    *
    * A request to `/static/files/css/styles.css` will try to read the `/home/app/static/css/styles.css` file.
    *
    * @param fileFilter
    *   file will exposed only if this function returns `true`.
    */
  def filesServerEndpoints[F[_]](
      prefix: EndpointInput[Unit]
  )(systemPath: String, fileFilter: List[String] => Boolean = _ => true): List[ServerEndpoint[Any, F]] =
    List(filesHeadServerEndpoint(prefix)(systemPath, fileFilter), filesGetServerEndpoint(prefix)(systemPath, fileFilter))

  /** A server endpoint, which exposes a single file from local storage found at `systemPath`, using the given `path`.
    *
    * {{{
    * fileGetServerEndpoint("static" / "hello.html")("/home/app/static/data.html")
    * }}}
    */
  def fileGetServerEndpoint[F[_]](path: EndpointInput[Unit])(systemPath: String): ServerEndpoint[Any, F] =
    ServerEndpoint.public(removePath(filesGetEndpoint(path)), (m: MonadError[F]) => Files.get(systemPath)(m))

  /** A server endpoint, which exposes resources available from the given `classLoader`, using the given `prefix`. Typically, the prefix is
    * a path, but it can also contain other inputs. For example:
    *
    * {{{
    * resourcesGetServerEndpoint("static" / "files")(classOf[App].getClassLoader, "app")
    * }}}
    *
    * A request to `/static/files/css/styles.css` will try to read the `/app/css/styles.css` resource.
    *
    * @param resourceFilter
    *   resource will exposed only if this function returns `true`.
    */
  def resourcesGetServerEndpoint[F[_]](prefix: EndpointInput[Unit])(
      classLoader: ClassLoader,
      resourcePrefix: String,
      useGzippedIfAvailable: Boolean = false,
      resourceFilter: String => Boolean = _ => true
  ): ServerEndpoint[Any, F] =
    ServerEndpoint.public(
      resourcesGetEndpoint(prefix),
      (m: MonadError[F]) =>
        Resources(classLoader, resourcePrefix, useGzippedIfAvailable = useGzippedIfAvailable, resourceFilter = resourceFilter)(m)
    )

  /** A server endpoint, which exposes a single resource available from the given `classLoader` at `resourcePath`, using the given `path`.
    *
    * {{{
    * resourceGetServerEndpoint("static" / "hello.html")(classOf[App].getClassLoader, "app/data.html")
    * }}}
    */
  def resourceGetServerEndpoint[F[_]](prefix: EndpointInput[Unit])(
      classLoader: ClassLoader,
      resourcePath: String,
      useGzippedIfAvailable: Boolean = false
  ): ServerEndpoint[Any, F] =
    ServerEndpoint.public(
      removePath(resourcesGetEndpoint(prefix)),
      (m: MonadError[F]) => Resources(classLoader, resourcePath, useGzippedIfAvailable = useGzippedIfAvailable)(m)
    )

  private def removePath[T](e: Endpoint[Unit, StaticInput, StaticErrorOutput, StaticOutput[T], Any]) =
    e.mapIn(i => i.copy(path = Nil))(i => i.copy(path = Nil))
}
