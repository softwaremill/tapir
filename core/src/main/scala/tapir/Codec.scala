package tapir

import tapir.DecodeResult._

trait Codec[T, M <: MediaType] {
  def encodeOptional(t: T): Option[String]
  def decodeOptional(s: Option[String]): DecodeResult[T]
  def isOptional: Boolean
  def schema: Schema
  def mediaType: M

  def map[TT](f: T => TT)(g: TT => T): Codec[TT, M] = new MappedCodec[T, TT, M](this, f, g)
}

class MappedCodec[T, TT, M <: MediaType](nested: Codec[T, M], f: T => TT, g: TT => T) extends Codec[TT, M] {
  override def encodeOptional(t: TT): Option[String] = nested.encodeOptional(g(t))
  override def decodeOptional(s: Option[String]): DecodeResult[TT] = nested.decodeOptional(s).map(f)
  override def isOptional: Boolean = nested.isOptional
  override def schema: Schema = nested.schema
  override def mediaType: M = nested.mediaType
}

trait RequiredCodec[T, M <: MediaType] extends Codec[T, M] {
  def encode(t: T): String
  def decode(s: String): DecodeResult[T]

  override def encodeOptional(t: T): Option[String] = Some(encode(t))
  override def decodeOptional(s: Option[String]): DecodeResult[T] = s match {
    case None     => DecodeResult.Missing
    case Some(ss) => decode(ss)
  }

  def isOptional: Boolean = false
}

object Codec {
  type TextCodec[T] = Codec[T, MediaType.Text]
  type RequiredTextCodec[T] = RequiredCodec[T, MediaType.Text]

  type JsonCodec[T] = Codec[T, MediaType.Json]
  type RequiredJsonCodec[T] = RequiredCodec[T, MediaType.Json]

  implicit val stringTextCodec: RequiredTextCodec[String] = textCodec[String](identity, Schema.SString)
  implicit val shortTextCodec: RequiredTextCodec[Short] = textCodec[Short](_.toShort, Schema.SInteger)
  implicit val intTextCodec: RequiredTextCodec[Int] = textCodec[Int](_.toInt, Schema.SInteger)
  implicit val longTextCodec: RequiredTextCodec[Long] = textCodec[Long](_.toLong, Schema.SInteger)
  implicit val floatTextCodec: RequiredTextCodec[Float] = textCodec[Float](_.toFloat, Schema.SNumber)
  implicit val doubleTextCodec: RequiredTextCodec[Double] = textCodec[Double](_.toDouble, Schema.SNumber)
  implicit val booleanTextCodec: RequiredTextCodec[Boolean] = textCodec[Boolean](_.toBoolean, Schema.SBoolean)

  private def textCodec[T](parse: String => T, _schema: Schema): RequiredCodec[T, MediaType.Text] =
    new RequiredTextCodec[T] {
      override def encode(t: T): String = t.toString
      override def decode(s: String): DecodeResult[T] =
        try Value(parse(s))
        catch {
          case e: Exception => Error(s, e, "Cannot parse")
        }
      override def schema: Schema = _schema
      override def mediaType = MediaType.Text()
    }

  implicit def optionalCodec[T, M <: MediaType](implicit tm: RequiredCodec[T, M]): Codec[Option[T], M] =
    new Codec[Option[T], M] {
      override def encodeOptional(t: Option[T]): Option[String] = t.map(tm.encode)
      override def decodeOptional(s: Option[String]): DecodeResult[Option[T]] = s match {
        case None => DecodeResult.Value(None)
        case Some(ss) =>
          tm.decode(ss) match {
            case DecodeResult.Value(v)  => DecodeResult.Value(Some(v))
            case DecodeResult.Missing   => DecodeResult.Missing
            case de: DecodeResult.Error => de
          }
      }
      override def isOptional: Boolean = true
      override def schema: Schema = tm.schema
      override def mediaType: M = tm.mediaType
    }
}

sealed trait BasicValue
