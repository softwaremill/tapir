package tapir.model

import tapir.model.Part.FileNameDispositionParam

case class Part[T](name: String, otherDispositionParams: Map[String, String], headers: Seq[(String, String)], body: T) {
  def fileName(fn: String): Part[T] = copy(otherDispositionParams = otherDispositionParams + (FileNameDispositionParam -> fn))
  def fileName: Option[String] = otherDispositionParams.get(FileNameDispositionParam)

  def header(k: String, v: String): Part[T] = copy(headers = headers :+ ((k, v)))
  def header(k: String): Option[String] = headers.find(_._1 == k).map(_._2)
  def headersMap: Map[String, String] = headers.toMap
}

object Part {
  private val FileNameDispositionParam = "filename"

  def apply[T](name: String, body: T): Part[T] = Part(name, Map.empty, Nil, body)
  def apply[T](body: T): Part[T] = Part("", body)
}
