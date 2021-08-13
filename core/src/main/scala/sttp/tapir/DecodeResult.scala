package sttp.tapir

sealed trait DecodeResult[+T] {
  def map[TT](f: T => TT): DecodeResult[TT]
  def flatMap[U](f: T => DecodeResult[U]): DecodeResult[U]
}
object DecodeResult {
  sealed trait Failure extends DecodeResult[Nothing] {
    override def map[TT](f: Nothing => TT): DecodeResult[TT] = this
    override def flatMap[U](f: Nothing => DecodeResult[U]): DecodeResult[U] = this
  }

  case class Value[T](v: T) extends DecodeResult[T] {
    override def map[TT](f: T => TT): DecodeResult[TT] = Value(f(v))
    override def flatMap[U](f: T => DecodeResult[U]): DecodeResult[U] = f(v)
  }
  case object Missing extends Failure
  case class Multiple[R](vs: Seq[R]) extends Failure

  /** Any error that occurred while decoding the `original` value. */
  case class Error(original: String, error: Throwable) extends Failure
  object Error {
    case class JsonDecodeException(errors: List[JsonError], underlying: Throwable)
        extends Exception(
          if (errors.isEmpty) underlying.getMessage else errors.map(_.message).mkString(", "),
          underlying
        )
    case class JsonError(msg: String, path: List[FieldName]) {
      def message: String = {
        val at = if (path.nonEmpty) s" at '${path.map(_.encodedName).mkString(".")}'" else ""
        msg + at
      }
    }
  }
  case class Mismatch(expected: String, actual: String) extends Failure

  /** A validation error that occurred when decoding the value, that is, when some `Validator` failed. */
  case class InvalidValue(errors: List[ValidationError[_]]) extends Failure

  def sequence[T](results: Seq[DecodeResult[T]]): DecodeResult[Seq[T]] = {
    results.foldRight(Value(List.empty[T]): DecodeResult[Seq[T]]) { case (result, acc) =>
      (result, acc) match {
        case (Value(v), Value(vs)) => Value(v +: vs)
        case (Value(_), r)         => r
        case (df: Failure, _)      => df
      }
    }
  }

  def fromOption[T](o: Option[T]): DecodeResult[T] = o.map(Value(_)).getOrElse(Missing)
}
