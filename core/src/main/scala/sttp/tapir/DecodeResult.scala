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
        extends Exception(underlying.getMessage, underlying, true, false)
    case class JsonError(msg: String, path: List[FieldName])

    case class MultipartDecodeException(partFailures: List[(String, DecodeResult.Failure)])
        extends Exception(partFailures.toString, MultipartDecodeException.causeOrNull(partFailures), true, false)
    object MultipartDecodeException {
      def causeOrNull(partFailures: List[(String, DecodeResult.Failure)]): Throwable =
        partFailures.collectFirst { case (_, DecodeResult.Error(_, error)) => error }.orNull
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
  def fromEitherString[T](original: String, o: Either[String, T]): DecodeResult[T] = o match {
    case Left(e)      => DecodeResult.Error(original, new RuntimeException(e))
    case Right(value) => DecodeResult.Value(value)
  }
}
