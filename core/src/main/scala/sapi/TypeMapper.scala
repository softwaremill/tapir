package sapi

import scala.util.{Failure, Success, Try}

trait TypeMapper[T] {
  def toOptionalString(t: T): Option[String]
  def fromOptionalString(s: Option[String]): TypeMapper.Result[T]
  def isOptional: Boolean

  // TODO: add content / media type
}

trait RequiredTypeMapper[T] extends TypeMapper[T] {
  def toString(t: T): String
  def fromString(s: String): TypeMapper.Result[T]

  override def toOptionalString(t: T): Option[String] = Some(toString(t))
  override def fromOptionalString(s: Option[String]): TypeMapper.Result[T] = s match {
    case None     => TypeMapper.MissingValue
    case Some(ss) => fromString(ss)
  }
  def isOptional: Boolean = false
}

object TypeMapper {
  sealed trait Result[+T] {
    def getOrThrow(e: (Result[Nothing], Option[Throwable]) => Throwable): T
  }
  case class Value[T](v: T) extends Result[T] {
    def getOrThrow(e: (Result[Nothing], Option[Throwable]) => Throwable): T = v
  }
  case object MissingValue extends Result[Nothing] {
    def getOrThrow(e: (Result[Nothing], Option[Throwable]) => Throwable): Nothing = throw e(this, None)
  }
  case class DeserializationError(original: String, error: Throwable, message: String) extends Result[Nothing] {
    def getOrThrow(e: (Result[Nothing], Option[Throwable]) => Throwable): Nothing = throw e(this, Some(error))
  }

  implicit val stringTypeMapper: RequiredTypeMapper[String] = new RequiredTypeMapper[String] {
    override def toString(t: String): String = t
    override def fromString(s: String): Result[String] = Value(s)
  }
  implicit val intTypeMapper: RequiredTypeMapper[Int] = new RequiredTypeMapper[Int] {
    override def toString(t: Int): String = t.toString
    override def fromString(s: String): Result[Int] = Try(s.toInt) match {
      case Success(i) => Value(i)
      case Failure(e) => DeserializationError(s, e, e.getMessage)
    }
  }
  implicit val unitTypeMapper: RequiredTypeMapper[Unit] = new RequiredTypeMapper[Unit] {
    override def toString(t: Unit): String = ""
    override def fromString(s: String): Result[Unit] = Value(())
  }

  implicit def optionalTypeMapper[T](implicit tm: RequiredTypeMapper[T]): TypeMapper[Option[T]] = new TypeMapper[Option[T]] {
    override def toOptionalString(t: Option[T]): Option[String] = t.map(tm.toString)
    override def fromOptionalString(s: Option[String]): Result[Option[T]] = s match {
      case None => Value(None)
      case Some(ss) =>
        tm.fromString(ss) match {
          case Value(v)                 => Value(Some(v))
          case MissingValue             => MissingValue
          case de: DeserializationError => de
        }
    }
    override def isOptional: Boolean = true
  }
}
