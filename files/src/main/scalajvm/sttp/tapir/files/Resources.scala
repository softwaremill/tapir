package sttp.tapir.files

import sttp.model.MediaType
import sttp.monad.MonadError
import sttp.monad.syntax._

import java.io.{File, FileNotFoundException, InputStream}
import java.net.URL
import java.time.Instant

object Resources {
  def apply[F[_]: MonadError](
                               classLoader: ClassLoader,
                               resourcePrefix: String,
                               options: ResourcesOptions[F] = ResourcesOptions.default[F]
                             ): StaticInput => F[Either[StaticErrorOutput, StaticOutput[InputStream]]] = (resourcesInput: StaticInput) =>
    resources(classLoader, resourcePrefix.split("/").toList, options)(resourcesInput)

  private def resources[F[_]](
                               classLoader: ClassLoader,
                               resourcePrefix: List[String],
                               options: ResourcesOptions[F]
                             )(
                               resourcesInput: StaticInput
                             )(implicit
                               m: MonadError[F]
                             ): F[Either[StaticErrorOutput, StaticOutput[InputStream]]] = {
    def notFound = (Left(StaticErrorOutput.NotFound): Either[StaticErrorOutput, StaticOutput[InputStream]]).unit

    val nameComponents = resourcePrefix ++ resourcesInput.path
    if (options.resourceFilter(nameComponents)) {
      val useGzippedIfAvailable = options.useGzippedIfAvailable && resourcesInput.acceptEncoding.exists(_.equals("gzip"))
      m.blocking {
        resolveURL(classLoader, resourcePrefix, resourcesInput.path, options.defaultResource, useGzippedIfAvailable)
          .map { case (url, mt, enc) => readResource(options.useETags, resourcesInput, url, mt, enc) }
          .getOrElse(Left(StaticErrorOutput.NotFound))
      }.handleError { case _: FileNotFoundException =>
        notFound
      }
    } else notFound
  }

  private def resolveURL(
                          classLoader: ClassLoader,
                          resourcePrefix: List[String],
                          path: List[String],
                          default: Option[List[String]],
                          useGzippedIfAvailable: Boolean
                        ): Option[(URL, MediaType, Option[String])] = {
    val name = (resourcePrefix ++ path).mkString("/")

    val result = (if (useGzippedIfAvailable) Option(classLoader.getResource(name + ".gz")).map((_, MediaType.ApplicationGzip, Some("gzip")))
    else None)
      .orElse(Option(classLoader.getResource(name)).map((_, contentTypeFromName(name), None)))
      .orElse(default match {
        case None              => None
        case Some(defaultPath) => resolveURL(classLoader, resourcePrefix, defaultPath, None, useGzippedIfAvailable)
      })
      // making sure that the resulting path contains the original requested path
      .filter(_._1.toURI.toString.contains(resourcePrefix.mkString("/")))

    if (result.exists(r => isDirectory(classLoader, name, r._1)))
      resolveURL(classLoader, resourcePrefix, path :+ "index.html", default, useGzippedIfAvailable)
    else result
  }

  private def isDirectory(classLoader: ClassLoader, name: String, nameResource: URL): Boolean = {
    // https://stackoverflow.com/questions/20105554/is-there-a-way-to-tell-if-a-classpath-resource-is-a-file-or-a-directory
    if (nameResource.getProtocol == "file") new File(nameResource.getPath).isDirectory
    else classLoader.getResource(name + "/") != null
  }

  private def readResource[F[_]](
                                  useETags: Boolean,
                                  resourcesInput: StaticInput,
                                  url: URL,
                                  contentType: MediaType,
                                  contentEncoding: Option[String]
                                ): Either[StaticErrorOutput, StaticOutput[InputStream]] = {
    val conn = url.openConnection()

    val lastModified = conn.getLastModified
    val length = conn.getContentLengthLong

    val etag = if (useETags) Some(defaultETag(lastModified, length)) else None

    if (isModified(resourcesInput, etag, lastModified))
      Right(
        StaticOutput
          .Found(conn.getInputStream, Some(Instant.ofEpochMilli(lastModified)), Some(length), Some(contentType), etag, contentEncoding)
      )
    else Right(StaticOutput.NotModified)
  }
}

/** @param resourceFilter
 *   A resource will be exposed only if this function returns `true`.
 * @param defaultResource
 *   path segments (relative to the resource prefix from which resources are read) of the resource to return in case the one requested by
 *   the user isn't found. This is useful for SPA apps, where the same main application resource needs to be returned regardless of the
 *   path.
 */
case class ResourcesOptions[F[_]](
                                   useETags: Boolean,
                                   useGzippedIfAvailable: Boolean,
                                   resourceFilter: List[String] => Boolean,
                                   defaultResource: Option[List[String]]
                                 ) {
  def withUseGzippedIfAvailable: ResourcesOptions[F] = copy(useGzippedIfAvailable = true)
  def withUseETags: ResourcesOptions[F] = copy(useETags = true)

  /** A resource will be exposed only if this function returns `true`. */
  def resourceFilter(f: List[String] => Boolean): ResourcesOptions[F] = copy(resourceFilter = f)

  /** Path segments (relative to the resource prefix from which resources are read) of the resource to return in case the one requested by
   * the user isn't found. This is useful for SPA apps, where the same main application resource needs to be returned regardless of the
   * path.
   */
  def defaultResource(d: List[String]): ResourcesOptions[F] = copy(defaultResource = Some(d))
}
object ResourcesOptions {
  def default[F[_]]: ResourcesOptions[F] = ResourcesOptions(useETags = true, useGzippedIfAvailable = false, _ => true, None)
}
