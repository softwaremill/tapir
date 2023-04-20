package sttp.tapir.files

import sttp.model.MediaType
import sttp.monad.MonadError
import sttp.monad.syntax._

import java.io.File
import sttp.tapir.InputStreamSupplier
import sttp.tapir.ResourceRange
import sttp.tapir.RangeValue
import java.net.URL

import java.io.InputStream
import sttp.tapir.files.StaticInput
import Files._

object Resources {

  def head[F[_]](
      classLoader: ClassLoader,
      resourcePrefix: String,
      options: FilesOptions[F] = FilesOptions.default[F]
  ): MonadError[F] => StaticInput => F[Either[StaticErrorOutput, HeadOutput]] = { implicit monad => filesInput =>
    get(classLoader, resourcePrefix, options)(monad)(filesInput)
      .map(_.map(staticOutput => HeadOutput.fromStaticOutput(staticOutput)))
  }

  def get[F[_]](
      classLoader: ClassLoader,
      resourcePrefix: String,
      options: FilesOptions[F] = FilesOptions.default[F]
  ): MonadError[F] => StaticInput => F[Either[StaticErrorOutput, StaticOutput[ResourceRange]]] = { implicit monad => filesInput =>
    val resolveUrlFn: ResolveUrlFn = resolveResourceUrl(classLoader, resourcePrefix.split("/").toList, filesInput, options)
    files(filesInput, options, resolveUrlFn, resourceRangeFromUrl _)
  }

  private def resourceRangeFromUrl(
      url: URL,
      range: Option[RangeValue]
  ): ResourceRange = ResourceRange(
    UrlStreamSupplier(url),
    range
  )

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
                         Option(classLoader.getResource(name + ".gz")).map(ResolvedUrl(_, MediaType.ApplicationGzip, Some("gzip")))
                       else None)
        .orElse(Option(classLoader.getResource(name)).map(ResolvedUrl(_, contentTypeFromName(name), None)))
        .orElse(default match {
          case None => None
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

  case class UrlStreamSupplier(url: URL) extends InputStreamSupplier {
    override def openStream(): InputStream = url.openStream()
  }
}
