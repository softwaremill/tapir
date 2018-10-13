package sapi

import scala.util.{Success, Try}

trait TypeMapper[T] {
  def toString(t: T): String
  def fromString(s: String): Try[T]
}

object TypeMapper {
  implicit val stringTypeMapper: TypeMapper[String] = new TypeMapper[String] {
    override def toString(t: String): String = t
    override def fromString(s: String): Try[String] = Success(s)
  }
  implicit val intTypeMapper: TypeMapper[Int] = new TypeMapper[Int] {
    override def toString(t: Int): String = t.toString
    override def fromString(s: String): Try[Int] = Try(s.toInt)
  }
  implicit val unitTypeMapper: TypeMapper[Unit] = new TypeMapper[Unit] {
    override def toString(t: Unit): String = ""
    override def fromString(s: String): Try[Unit] = Success(())
  }
}
