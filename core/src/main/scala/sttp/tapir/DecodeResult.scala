package sttp.tapir

sealed trait DecodeResult[+T] {
  def map[TT](f: T => TT): DecodeResult[TT]
  def flatMap[U](f: T => DecodeResult[U]): DecodeResult[U]
}
sealed trait DecodeFailure extends DecodeResult[Nothing] {
  override def map[TT](f: Nothing => TT): DecodeResult[TT] = this
  override def flatMap[U](f: Nothing => DecodeResult[U]): DecodeResult[U] = this
}
object DecodeResult {
  case class Value[T](v: T) extends DecodeResult[T] {
    override def map[TT](f: T => TT): DecodeResult[TT] = Value(f(v))
    override def flatMap[U](f: T => DecodeResult[U]): DecodeResult[U] = f(v)
  }
  case object Missing extends DecodeFailure
  case class Multiple[R](vs: Seq[R]) extends DecodeFailure
  case class Error(original: String, error: Throwable) extends DecodeFailure
  case class Mismatch(expected: String, actual: String) extends DecodeFailure
  case class InvalidValue(errors: List[ValidationError[_]]) extends DecodeFailure

  def sequence[T](results: Seq[DecodeResult[T]]): DecodeResult[Seq[T]] = {
    results.foldRight(Value(List.empty[T]): DecodeResult[Seq[T]]) {
      case (result, acc) =>
        (result, acc) match {
          case (Value(v), Value(vs))  => Value(v +: vs)
          case (Value(_), r)          => r
          case (df: DecodeFailure, _) => df
        }
    }
  }

  def fromOption[T](o: Option[T]): DecodeResult[T] = o.map(Value(_)).getOrElse(Missing)
}
