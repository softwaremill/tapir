package com.softwaremill.swagger
import scala.util.Try

trait TypeMapper[T] {
  def toString(t: T): String
  def fromString(s: String): Option[T]
}

object TypeMapper {
  implicit val stringTypeMapper: TypeMapper[String] = new TypeMapper[String] {
    override def toString(t: String): String = t
    override def fromString(s: String): Option[String] = Some(s)
  }
  implicit val intTypeMapper: TypeMapper[Int] = new TypeMapper[Int] {
    override def toString(t: Int): String = t.toString
    override def fromString(s: String): Option[Int] = Try(s.toInt).toOption
  }
}
