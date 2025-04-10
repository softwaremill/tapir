package sttp.tapir.codegen.util

case class Location(path: String, method: String) {
  override def toString: String = s"${method.toUpperCase} ${path}"
}

object ErrUtils {
  def bail(msg: String)(implicit location: Location): Nothing = throw new NotImplementedError(s"$msg at $location")
}
