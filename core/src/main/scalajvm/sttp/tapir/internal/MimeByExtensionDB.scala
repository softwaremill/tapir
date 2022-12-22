package sttp.tapir.internal

import sttp.model.MediaType

import scala.io.Source

private[tapir] object MimeByExtensionDB {
  private val mimeTypes: Map[String, MediaType] = {
    val s = Source.fromURL(getClass.getResource("/mimeByExtensions.txt"))
    val pairs =
      try
        s
          .getLines()
          .toList
          .flatMap(line =>
            line.split(" ", 2) match {
              case Array(ext, mimeType) => MediaType.parse(mimeType).map(ext -> _).toOption.toList
              case _                    => Nil
            }
          )
      finally s.close()

    Map(pairs: _*)
  }

  def apply(extension: String): Option[MediaType] = mimeTypes.get(extension.toLowerCase)
}
