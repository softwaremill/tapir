package sapi

import io.circe.{Json, Printer}

import scala.util.{Failure, Success, Try}

/**
  * Data serialization format - how data is represented as text, with U as the intermediate format.
  *
  * @tparam U Intermediate format type
  */
trait Format[U] {
  def encodeString(s: String): U
  def encodeInt(i: Int): U
  def encodeObject(m: Iterable[(String, U)]): U

  def decodeString(u: U): DecodeResult[String]
  def decodeInt(u: U): DecodeResult[Int]
  def decodeObject(u: U): DecodeResult[Iterable[(String, U)]]

  def toString(u: U): String
  def fromString(s: String): DecodeResult[U]

  def contentType: String
}

object StringFormat extends Format[String] {
  override def encodeString(s: String): String = s
  override def encodeInt(i: Int): String = i.toString
  override def encodeObject(m: Iterable[(String, String)]): String = m.map { case (k, v) => s"$k=$v" }.mkString(",")

  override def decodeString(u: String): DecodeResult[String] = DecodeResult.Value(u)
  override def decodeInt(u: String): DecodeResult[Int] = Try(u.toInt) match {
    case Success(i) => DecodeResult.Value(i)
    case Failure(e) => DecodeResult.Error(u, e, "Cannot parse integer")
  }
  override def decodeObject(u: String): DecodeResult[Iterable[(String, String)]] = {
    val m = u
      .split(",")
      .map { p =>
        val Array(k, v) = p.split("=", 2) // TODO validate
        k -> v
      }
    DecodeResult.Value(m)
  }

  override def toString(u: String): String = u
  override def fromString(s: String): DecodeResult[String] = DecodeResult.Value(s)

  override def contentType: String = "text/plain"
}

object JsonFormat extends Format[Json] {
  override def encodeString(s: String): Json = Json.fromString(s)
  override def encodeInt(i: Int): Json = Json.fromInt(i)
  override def encodeObject(m: Iterable[(String, Json)]): Json = Json.fromFields(m)

  override def decodeString(u: Json): DecodeResult[String] = u.asString match {
    case Some(s) => DecodeResult.Value(s)
    case None    => DecodeResult.Error(Printer.noSpaces.pretty(u), new RuntimeException("Not a string"), "Not a string")
  }
  override def decodeInt(u: Json): DecodeResult[Int] = u.asNumber.flatMap(_.toInt) match {
    case Some(s) => DecodeResult.Value(s)
    case None    => DecodeResult.Error(Printer.noSpaces.pretty(u), new RuntimeException("Not an int"), "Not an int")
  }
  override def decodeObject(u: Json): DecodeResult[Iterable[(String, Json)]] = u.asObject match {
    case Some(obj) => DecodeResult.Value(obj.toIterable)
    case None      => DecodeResult.Error(Printer.noSpaces.pretty(u), new RuntimeException("Not an object"), "Not an object")
  }

  override def toString(u: Json): String = Printer.noSpaces.pretty(u)
  override def fromString(s: String): DecodeResult[Json] = io.circe.parser.parse(s) match {
    case Left(pf)    => DecodeResult.Error(s, pf.underlying, pf.message)
    case Right(json) => DecodeResult.Value(json)
  }

  override def contentType: String = "application/Json"
}
