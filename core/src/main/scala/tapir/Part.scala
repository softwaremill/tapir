package tapir

import Part.FileNameDispositionParam

// TODO: move this & MultiQueryParams to a http/model package?
case class Part[T](name: String, otherDispositionParams: Map[String, String], headers: Seq[(String, String)], body: T) {
  def fileName(fn: String): Part[T] = copy(otherDispositionParams = otherDispositionParams + (FileNameDispositionParam -> fn))
  def fileName: Option[String] = otherDispositionParams.get(FileNameDispositionParam)

  def headersMap: Map[String, String] = headers.toMap
}

object Part {
  private val FileNameDispositionParam = "filename"

  def apply[T](name: String, body: T): Part[T] = Part(name, Map.empty, Nil, body)
}
