package sttp.tapir.files

import sttp.model.Header
import sttp.model.HeaderNames
import sttp.model.MediaType
import sttp.model.StatusCode
import sttp.model.headers.ETag
import sttp.model.headers.Range
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint

import java.time.Instant

/** Static content endpoints, including files and resources. */
trait TapirStaticContentEndpoints {
  // we can't use oneOfVariant and mapTo, since mapTo doesn't work with body fields of type T

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

  private val acceptEncodingHeader: EndpointIO[List[String]] =
    header[Option[String]](HeaderNames.AcceptEncoding).mapDecode[List[String]] {
      case None      => DecodeResult.Value(List.empty)
      case Some(str) => DecodeResult.Value(str.split(",").map(_.trim).toList)
    }(es => Option(es.mkString(",")).filter(_.nonEmpty))

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
  private val contentEncodingHeader: EndpointIO[Option[String]] = header[Option[String]](HeaderNames.ContentEncoding)

  private def staticEndpoint[T](
      method: Endpoint[Unit, Unit, Unit, Unit, Any],
      body: EndpointOutput[T]
  ): PublicEndpoint[StaticInput, StaticErrorOutput, StaticOutput[T], Any] = {
    method
      .in(
        pathsWithoutDots
          .and(ifNoneMatchHeader)
          .and(ifModifiedSinceHeader)
          .and(rangeHeader)
          .and(acceptEncodingHeader)
          .mapTo[StaticInput]
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
              .and(acceptRangesHeader)
              .and(contentEncodingHeader)
              .map[StaticOutput.Found[T]](
                (t: (T, Option[Instant], Option[Long], Option[MediaType], Option[ETag], Option[String], Option[String])) =>
                  StaticOutput.Found(t._1, t._2, t._3, t._4, t._5, t._6, t._7)
              )(fo => (fo.body, fo.lastModified, fo.contentLength, fo.contentType, fo.etag, fo.acceptRanges, fo.contentEncoding)),
            classOf[StaticOutput.Found[T]]
          )
        )
      )
  }

  private lazy val staticHeadEndpoint: PublicEndpoint[StaticInput, StaticErrorOutput, StaticOutput[Unit], Any] =
    staticEndpoint(endpoint.head, emptyOutput)

  lazy val staticFilesGetEndpoint: PublicEndpoint[StaticInput, StaticErrorOutput, StaticOutput[FileRange], Any] = staticEndpoint(
    endpoint.get,
    fileRangeBody
  )

  lazy val staticResourcesGetEndpoint: PublicEndpoint[StaticInput, StaticErrorOutput, StaticOutput[InputStreamRange], Any] =
    staticEndpoint(endpoint.get, inputStreamRangeBody)

  def staticFilesGetEndpoint(prefix: EndpointInput[Unit]): PublicEndpoint[StaticInput, StaticErrorOutput, StaticOutput[FileRange], Any] =
    staticFilesGetEndpoint.prependIn(prefix)

  def staticResourcesGetEndpoint(
      prefix: EndpointInput[Unit]
  ): PublicEndpoint[StaticInput, StaticErrorOutput, StaticOutput[InputStreamRange], Any] =
    staticResourcesGetEndpoint.prependIn(prefix)

  @scala.annotation.tailrec
  private def addHeaders[O](
      ep: PublicEndpoint[StaticInput, StaticErrorOutput, StaticOutput[O], Any],
      headers: List[Header]
  ): PublicEndpoint[StaticInput, StaticErrorOutput, StaticOutput[O], Any] = headers match {
    case Nil     => ep
    case h :: hs => addHeaders(ep.out(header(h)), hs)
  }

  /** A server endpoint, which exposes files from local storage found at `systemPath`, using the given `prefix`. Typically, the prefix is a
    * path, but it can also contain other inputs. For example:
    *
    * {{{
    * staticFilesGetServerEndpoint("static" / "files")("/home/app/static")
    * }}}
    *
    * A request to `/static/files/css/styles.css` will try to read the `/home/app/static/css/styles.css` file.
    */
  def staticFilesGetServerEndpoint[F[_]](prefix: EndpointInput[Unit])(
      systemPath: String,
      options: FilesOptions[F] = FilesOptions.default[F],
      extraHeaders: List[Header] = Nil
  ): ServerEndpoint[Any, F] =
    ServerEndpoint.public(addHeaders(staticFilesGetEndpoint.prependIn(prefix), extraHeaders), Files.get(systemPath, options))

  /** A server endpoint, which exposes a single file from local storage found at `systemPath`, using the given `path`.
    *
    * {{{
    * staticFileGetServerEndpoint("static" / "hello.html")("/home/app/static/data.html")
    * }}}
    */
  def staticFileGetServerEndpoint[F[_]](prefix: EndpointInput[Unit])(
      systemPath: String,
      extraHeaders: List[Header] = Nil
  ): ServerEndpoint[Any, F] =
    ServerEndpoint.public(
      removePath(addHeaders(staticFilesGetEndpoint(prefix), extraHeaders)),
      (m: MonadError[F]) => Files.get(systemPath)(m)
    )

  /** A server endpoint, used to verify if sever supports range requests for file under particular path Additionally it verify file
    * existence and returns its size
    */
  def staticFilesHeadServerEndpoint[F[_]](prefix: EndpointInput[Unit])(
      systemPath: String,
      options: FilesOptions[F] = FilesOptions.default[F],
      extraHeaders: List[Header] = Nil
  ): ServerEndpoint[Any, F] =
    ServerEndpoint.public(addHeaders(staticHeadEndpoint.prependIn(prefix), extraHeaders), Files.head(systemPath, options))

  /** Create a pair of endpoints (head, get) for exposing files from local storage found at `systemPath`, using the given `prefix`.
    * Typically, the prefix is a path, but it can also contain other inputs. For example:
    *
    * {{{
    * staticFilesServerEndpoints("static" / "files")("/home/app/static")
    * }}}
    *
    * A request to `/static/files/css/styles.css` will try to read the `/home/app/static/css/styles.css` file.
    */
  def staticFilesServerEndpoints[F[_]](prefix: EndpointInput[Unit])(
      systemPath: String,
      options: FilesOptions[F] = FilesOptions.default[F],
      extraHeaders: List[Header] = Nil
  ): List[ServerEndpoint[Any, F]] =
    List(
      staticFilesHeadServerEndpoint(prefix)(systemPath, options, extraHeaders),
      staticFilesGetServerEndpoint(prefix)(systemPath, options, extraHeaders)
    )

  /** A server endpoint, which exposes resources available from the given `classLoader`, using the given `prefix`. Typically, the prefix is
    * a path, but it can also contain other inputs. For example:
    *
    * {{{
    * staticResourcesGetServerEndpoint("static" / "files")(classOf[App].getClassLoader, "app")
    * }}}
    *
    * A request to `/static/files/css/styles.css` will try to read the `/app/css/styles.css` resource.
    */
  def staticResourcesGetServerEndpoint[F[_]](prefix: EndpointInput[Unit])(
      classLoader: ClassLoader,
      resourcePrefix: String,
      options: FilesOptions[F] = FilesOptions.default[F],
      extraHeaders: List[Header] = Nil
  ): ServerEndpoint[Any, F] =
    ServerEndpoint.public[StaticInput, StaticErrorOutput, StaticOutput[InputStreamRange], Any, F](
      addHeaders(staticResourcesGetEndpoint(prefix), extraHeaders),
      (m: MonadError[F]) => Resources.get(classLoader, resourcePrefix, options)(m)
    )

  /** A server endpoint, which exposes a single resource available from the given `classLoader` at `resourcePath`, using the given `path`.
    *
    * {{{
    * staticResourceGetServerEndpoint("static" / "hello.html")(classOf[App].getClassLoader, "app/data.html")
    * }}}
    */
  def staticResourceGetServerEndpoint[F[_]](prefix: EndpointInput[Unit])(
      classLoader: ClassLoader,
      resourcePath: String,
      options: FilesOptions[F] = FilesOptions.default[F],
      extraHeaders: List[Header] = Nil
  ): ServerEndpoint[Any, F] =
    ServerEndpoint.public(
      removePath(addHeaders(staticResourcesGetEndpoint(prefix), extraHeaders)),
      (m: MonadError[F]) => Resources.get(classLoader, resourcePath, options)(m)
    )

  /** A server endpoint, which can be used to verify the existence of a resource under given path.
    */
  def staticResourcesHeadServerEndpoint[F[_]](prefix: EndpointInput[Unit])(
      classLoader: ClassLoader,
      resourcePath: String,
      options: FilesOptions[F] = FilesOptions.default[F],
      extraHeaders: List[Header] = Nil
  ): ServerEndpoint[Any, F] =
    ServerEndpoint.public(
      addHeaders(staticHeadEndpoint.prependIn(prefix), extraHeaders),
      Resources.head(classLoader, resourcePath, options)
    )

  private def removePath[T](e: Endpoint[Unit, StaticInput, StaticErrorOutput, StaticOutput[T], Any]) =
    e.mapIn(i => i.copy(path = Nil))(i => i.copy(path = Nil))

  /** Create a pair of endpoints (head, get) for exposing resources available from the given `classLoader`, using the given `prefix`.
    * Typically, the prefix is a path, but it can also contain other inputs. For example:
    *
    * {{{
    * resourcesServerEndpoints("static" / "files")(classOf[App].getClassLoader, "app")
    * }}}
    *
    * A request to `/static/files/css/styles.css` will try to read the `/app/css/styles.css` resource.
    */
  def staticResourcesServerEndpoints[F[_]](prefix: EndpointInput[Unit])(
      classLoader: ClassLoader,
      resourcePath: String,
      options: FilesOptions[F] = FilesOptions.default[F],
      extraHeaders: List[Header] = Nil
  ): List[ServerEndpoint[Any, F]] =
    List(
      staticResourcesHeadServerEndpoint(prefix)(classLoader, resourcePath, options, extraHeaders),
      staticResourcesGetServerEndpoint(prefix)(classLoader, resourcePath, options, extraHeaders)
    )
}
