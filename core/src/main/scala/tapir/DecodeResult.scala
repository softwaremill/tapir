package tapir

sealed trait DecodeResult[+T] {
  def getOrThrow(e: (DecodeResult[Nothing], Option[Throwable]) => Throwable): T
}
object DecodeResult {
  case class Value[T](v: T) extends DecodeResult[T] {
    def getOrThrow(e: (DecodeResult[Nothing], Option[Throwable]) => Throwable): T = v
  }
  case object Missing extends DecodeResult[Nothing] {
    def getOrThrow(e: (DecodeResult[Nothing], Option[Throwable]) => Throwable): Nothing = throw e(this, None)
  }
  case class Error(original: String, error: Throwable, message: String) extends DecodeResult[Nothing] {
    def getOrThrow(e: (DecodeResult[Nothing], Option[Throwable]) => Throwable): Nothing = throw e(this, Some(error))
  }

  // TODO: to reduce allocations, maybe replace with exceptions (which would all be handled by formats / type mappers?)
}
