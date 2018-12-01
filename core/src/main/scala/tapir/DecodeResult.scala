package tapir

sealed trait DecodeResult[+T] {
  def getOrThrow(e: (DecodeResult[Nothing], Option[Throwable]) => Throwable): T
  def map[TT](f: T => TT): DecodeResult[TT]
}
object DecodeResult {
  case class Value[T](v: T) extends DecodeResult[T] {
    def getOrThrow(e: (DecodeResult[Nothing], Option[Throwable]) => Throwable): T = v
    override def map[TT](f: T => TT): DecodeResult[TT] = Value(f(v))
  }
  case object Missing extends DecodeResult[Nothing] {
    def getOrThrow(e: (DecodeResult[Nothing], Option[Throwable]) => Throwable): Nothing = throw e(this, None)
    override def map[TT](f: Nothing => TT): DecodeResult[TT] = this
  }
  case class Error(original: String, error: Throwable, message: String) extends DecodeResult[Nothing] {
    def getOrThrow(e: (DecodeResult[Nothing], Option[Throwable]) => Throwable): Nothing = throw e(this, Some(error))
    override def map[TT](f: Nothing => TT): DecodeResult[TT] = this
  }

  // TODO: to reduce allocations, maybe replace with exceptions (which would all be handled by formats / type mappers?)
}
