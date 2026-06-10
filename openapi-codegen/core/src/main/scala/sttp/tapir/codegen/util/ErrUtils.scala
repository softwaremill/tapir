package sttp.tapir.codegen.util

case class Location(path: String, method: String) {
  override def toString: String = s"${method.toUpperCase} ${path}"
}

object ErrUtils {
  def bail(msg: String, maybeCause: Option[Throwable] = None)(implicit location: Location): Nothing = {
    val err = new NotImplementedError(s"$msg at $location")
    maybeCause.foreach(err.initCause)
    throw err
  }
}
