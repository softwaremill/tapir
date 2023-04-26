package sttp.tapir.static

import sttp.model.headers.{ETag, Range}
import sttp.model.{Header, HeaderNames, MediaType, StatusCode}
import sttp.monad.MonadError
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.{FileRange, _}

import java.io.InputStream
import java.time.Instant

/** Static content endpoints, including files and resources. */
trait TapirStaticContentEndpoints {
  // we can't use oneOfVariant and mapTo since they are macros, defined in the same compilation unit

  private val pathsWithoutDots: EndpointInput[List[String]] =
    paths.mapDecode(ps =>
      // a single path segment might contain / as well
      if (ps.exists(p => p == "" || p == "." || p == ".." || p.startsWith("../") || p.endsWith("/..") || p.contains("/../")))
        DecodeResult.Error(ps.mkString("/"), new RuntimeException(s"Incorrect path: ${ps.mkString("/")}"))
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
  private def contentLengthHeader: EndpointIO[Option[Long]] = header[Option[Long]](HeaderNames.ContentLength)
  private val etagHeader: EndpointIO[Option[ETag]] = header[Option[ETag]](HeaderNames.Etag)
  private val rangeHeader: EndpointIO[Option[Range]] = header[Option[Range]](HeaderNames.Range)
  private def acceptRangesHeader: EndpointIO[Option[String]] = header[Option[String]](HeaderNames.AcceptRanges)
  private val acceptEncodingHeader: EndpointIO[Option[String]] = header[Option[String]](HeaderNames.AcceptEncoding)
  private val contentEncodingHeader: EndpointIO[Option[String]] = header[Option[String]](HeaderNames.ContentEncoding)

  private def staticGetEndpoint[T](
      body: EndpointOutput[T]
  ): PublicEndpoint[StaticInput, StaticErrorOutput, StaticOutput[T], Any] = {
    endpoint.get
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
              .and(contentLengthHeader)
              .and(contentTypeHeader)
              .and(etagHeader)
              .and(acceptRangesHeader)
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
              .and(contentLengthHeader)
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

  private lazy val staticHeadEndpoint: PublicEndpoint[HeadInput, StaticErrorOutput, HeadOutput, Any] = {
    endpoint.head
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
            acceptRangesHeader
              .and(contentLengthHeader)
              .and(contentTypeHeader)
              .map[HeadOutput.Found]((t: (Option[String], Option[Long], Option[MediaType])) => HeadOutput.Found(t._1, t._2, t._3))(fo =>
                (fo.acceptRanges, fo.contentLength, fo.contentType)
              ),
            classOf[HeadOutput.Found]
          )
        )
      )
  }

  @deprecated("Use sttp.tapir.files.staticFilesGetEndpoint")
  lazy val filesGetEndpoint: PublicEndpoint[StaticInput, StaticErrorOutput, StaticOutput[FileRange], Any] = staticGetEndpoint(fileRangeBody)

  @deprecated("Use sttp.tapir.files.staticResourcesGetEndpoint")
  lazy val resourcesGetEndpoint: PublicEndpoint[StaticInput, StaticErrorOutput, StaticOutput[InputStream], Any] =
    staticGetEndpoint(inputStreamBody)

  @deprecated("Use sttp.tapir.files.staticFilesGetEndpoint")
  def filesGetEndpoint(prefix: EndpointInput[Unit]): PublicEndpoint[StaticInput, StaticErrorOutput, StaticOutput[FileRange], Any] =
    filesGetEndpoint.prependIn(prefix)
  
  @deprecated("Use sttp.tapir.files.staticResourcesGetEndpoint")
  def resourcesGetEndpoint(prefix: EndpointInput[Unit]): PublicEndpoint[StaticInput, StaticErrorOutput, StaticOutput[InputStream], Any] =
    resourcesGetEndpoint.prependIn(prefix)

  /** A server endpoint, which exposes files from local storage found at `systemPath`, using the given `prefix`. Typically, the prefix is a
    * path, but it can also contain other inputs. For example:
    *
    * {{{
    * filesGetServerEndpoint("static" / "files")("/home/app/static")
    * }}}
    *
    * A request to `/static/files/css/styles.css` will try to read the `/home/app/static/css/styles.css` file.
    */
  @deprecated("Use sttp.tapir.files.staticFilesGetServerEndpoint")
  def filesGetServerEndpoint[F[_]](
      prefix: EndpointInput[Unit]
  )(systemPath: String, options: FilesOptions[F] = FilesOptions.default[F]): ServerEndpoint[Any, F] =
    ServerEndpoint.public(filesGetEndpoint(prefix), Files.get(systemPath, options))

  /** A server endpoint, used to verify if sever supports range requests for file under particular path Additionally it verify file
    * existence and returns its size
    */
  @deprecated("Use sttp.tapir.files.staticFilesHeadServerEndpoint")
  def filesHeadServerEndpoint[F[_]](
      prefix: EndpointInput[Unit]
  )(systemPath: String, options: FilesOptions[F] = FilesOptions.default[F]): ServerEndpoint[Any, F] =
    ServerEndpoint.public(staticHeadEndpoint.prependIn(prefix), Files.head(systemPath, options))

  /** Create a pair of endpoints (head, get) for exposing files from local storage found at `systemPath`, using the given `prefix`.
    * Typically, the prefix is a path, but it can also contain other inputs. For example:
    *
    * {{{
    * filesServerEndpoints("static" / "files")("/home/app/static")
    * }}}
    *
    * A request to `/static/files/css/styles.css` will try to read the `/home/app/static/css/styles.css` file.
    */
  @deprecated("Use sttp.tapir.files.staticFilesServerEndpoints")
  def filesServerEndpoints[F[_]](
      prefix: EndpointInput[Unit]
  )(systemPath: String, options: FilesOptions[F] = FilesOptions.default[F]): List[ServerEndpoint[Any, F]] =
    List(filesHeadServerEndpoint(prefix)(systemPath, options), filesGetServerEndpoint(prefix)(systemPath, options))

  /** A server endpoint, which exposes a single file from local storage found at `systemPath`, using the given `path`.
    *
    * {{{
    * fileGetServerEndpoint("static" / "hello.html")("/home/app/static/data.html")
    * }}}
    */
  @deprecated("Use sttp.tapir.files.staticFileGetServerEndpoint")
  def fileGetServerEndpoint[F[_]](prefix: EndpointInput[Unit])(systemPath: String): ServerEndpoint[Any, F] =
    ServerEndpoint.public(removePath(filesGetEndpoint(prefix)), (m: MonadError[F]) => Files.get(systemPath)(m))

  /** A server endpoint, which exposes resources available from the given `classLoader`, using the given `prefix`. Typically, the prefix is
    * a path, but it can also contain other inputs. For example:
    *
    * {{{
    * resourcesGetServerEndpoint("static" / "files")(classOf[App].getClassLoader, "app")
    * }}}
    *
    * A request to `/static/files/css/styles.css` will try to read the `/app/css/styles.css` resource.
    */
  @deprecated("Use sttp.tapir.files.staticResourcesGetServerEndpoint")
  def resourcesGetServerEndpoint[F[_]](prefix: EndpointInput[Unit])(
      classLoader: ClassLoader,
      resourcePrefix: String,
      options: ResourcesOptions[F] = ResourcesOptions.default[F]
  ): ServerEndpoint[Any, F] =
    ServerEndpoint.public(
      resourcesGetEndpoint(prefix),
      (m: MonadError[F]) => Resources(classLoader, resourcePrefix, options)(m)
    )

  /** A server endpoint, which exposes a single resource available from the given `classLoader` at `resourcePath`, using the given `path`.
    *
    * {{{
    * resourceGetServerEndpoint("static" / "hello.html")(classOf[App].getClassLoader, "app/data.html")
    * }}}
    */
  @deprecated("Use sttp.tapir.files.staticResourceGetServerEndpoint")
  def resourceGetServerEndpoint[F[_]](prefix: EndpointInput[Unit])(
      classLoader: ClassLoader,
      resourcePath: String,
      options: ResourcesOptions[F] = ResourcesOptions.default[F]
  ): ServerEndpoint[Any, F] =
    ServerEndpoint.public(
      removePath(resourcesGetEndpoint(prefix)),
      (m: MonadError[F]) => Resources(classLoader, resourcePath, options)(m)
    )

  private def removePath[T](e: Endpoint[Unit, StaticInput, StaticErrorOutput, StaticOutput[T], Any]) =
    e.mapIn(i => i.copy(path = Nil))(i => i.copy(path = Nil))
}
