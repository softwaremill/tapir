package sttp.tapir.files

import sttp.model.MediaType
import sttp.monad.MonadError
import sttp.monad.syntax._

import java.io.File
import sttp.tapir.InputStreamRange
import sttp.tapir.RangeValue
import java.net.URL

import sttp.tapir.files.StaticInput
import Files._

object Resources {

  def head[F[_]](
      classLoader: ClassLoader,
      resourcePrefix: String,
      options: FilesOptions[F] = FilesOptions.default[F]
  ): MonadError[F] => StaticInput => F[Either[StaticErrorOutput, StaticOutput[Unit]]] = { implicit monad => filesInput =>
    get(classLoader, resourcePrefix, options)(monad)(filesInput)
      .map(_.map(_.withoutBody))
  }

  def get[F[_]](
      classLoader: ClassLoader,
      resourcePrefix: String,
      options: FilesOptions[F] = FilesOptions.default[F]
  ): MonadError[F] => StaticInput => F[Either[StaticErrorOutput, StaticOutput[InputStreamRange]]] = { implicit monad => filesInput =>
    val resolveUrlFn: ResolveUrlFn = resolveResourceUrl(classLoader, resourcePrefix.split("/").toList, filesInput, options)
    files(filesInput, options, resolveUrlFn, resourceRangeFromUrl)
  }

  private def resourceRangeFromUrl(
      url: URL,
      range: Option[RangeValue]
  ): InputStreamRange = InputStreamRange(
    () => url.openStream(),
    range
  )

  /** Creates a function of type ResolveUrlFn, which is capable of taking a relative path as a list of string segments, and finding the
    * actual full Url of a file available as a resource under a classLoader, considering additional parameters. For example, with a root
    * resource prefix of /config/files/ it can create a function which takies List("dir1", "dir2", "file.txt") and tries to resolve
    * /config/files/dir1/dir2/file.txt into a resource Url. The final resolved file may also be resolved to a pre-gzipped sibling, an
    * index.html file, or a default file given as a fallback, all depending on additional parameters. See also Files.resolveSystemPathUrl
    * for an equivalent of this function but for files from the filesystem.
    *
    * @param classLoader
    *   the class loader that will be used to call .getResource
    * @param resourcePrefix
    *   root resource path prefix represented as string segments
    * @param input
    *   request input parameters like path and headers, used together with options to apply filtering and look for possible pre-gzipped
    *   files if they are accepted
    * @param options
    *   additional options of the endpoint, defining filtering rules and pre-gzipped file support
    * @return
    *   a function which can be used in general file resolution logic. This function takes path segments and an optional default fallback
    *   path segments and tries to resolve the file, then returns its full Url.
    */
  private def resolveResourceUrl[F[_]](
      classLoader: ClassLoader,
      resourcePrefix: List[String],
      input: StaticInput,
      options: FilesOptions[F]
  ): ResolveUrlFn = {

    val nameComponents = resourcePrefix ++ input.path

    def resolveRec(path: List[String], default: Option[List[String]]): Option[ResolvedUrl] = {
      val name = (resourcePrefix ++ path).mkString("/")
      val resultOpt = (if (useGzippedIfAvailable(input, options))
                         Option(classLoader.getResource(name + ".gz")).map(ResolvedUrl(_, contentTypeFromName(name), Some("gzip")))
                       else None)
        .orElse(Option(classLoader.getResource(name)).map(ResolvedUrl(_, contentTypeFromName(name), None)))
        .orElse(default match {
          case None              => None
          case Some(defaultPath) =>
            resolveRec(path = defaultPath, default = None)
        })
        // making sure that the resulting path contains the original requested path
        .filter(_.url.toURI.toString.contains(resourcePrefix.mkString("/")))

      if (resultOpt.exists(r => isDirectory(classLoader, name, r.url)))
        resolveRec(path :+ "index.html", default)
      else resultOpt
    }

    if (!options.fileFilter(nameComponents))
      (_, _) => LeftUrlNotFound
    else
      Function.untupled((resolveRec _).tupled.andThen(_.map(Right(_)).getOrElse(LeftUrlNotFound)))
  }

  private def isDirectory(classLoader: ClassLoader, name: String, nameResource: URL): Boolean = {
    // https://stackoverflow.com/questions/20105554/is-there-a-way-to-tell-if-a-classpath-resource-is-a-file-or-a-directory
    if (nameResource.getProtocol == "file") new File(nameResource.getPath).isDirectory
    else classLoader.getResource(name + "/") != null
  }

}
