package tapir

import tapir.DecodeResult._

trait TypeMapper[T, M <: MediaType] {
  def toOptionalString(t: T): Option[String]
  def fromOptionalString(s: Option[String]): DecodeResult[T]
  def isOptional: Boolean
  def schema: Schema
  def mediaType: M

  def map[TT](f: T => TT)(g: TT => T): TypeMapper[TT, M] = new MappedTypeMapper[T, TT, M](this, f, g)
}

class MappedTypeMapper[T, TT, M <: MediaType](nested: TypeMapper[T, M], f: T => TT, g: TT => T) extends TypeMapper[TT, M] {
  override def toOptionalString(t: TT): Option[String] = nested.toOptionalString(g(t))
  override def fromOptionalString(s: Option[String]): DecodeResult[TT] = nested.fromOptionalString(s).map(f)
  override def isOptional: Boolean = nested.isOptional
  override def schema: Schema = nested.schema
  override def mediaType: M = nested.mediaType
}

trait RequiredTypeMapper[T, M <: MediaType] extends TypeMapper[T, M] {
  def toString(t: T): String
  def fromString(s: String): DecodeResult[T]

  override def toOptionalString(t: T): Option[String] = Some(toString(t))
  override def fromOptionalString(s: Option[String]): DecodeResult[T] = s match {
    case None     => DecodeResult.Missing
    case Some(ss) => fromString(ss)
  }

  def isOptional: Boolean = false
}

object TypeMapper {
  type TextTypeMapper[T] = TypeMapper[T, MediaType.Text]
  type RequiredTextTypeMapper[T] = RequiredTypeMapper[T, MediaType.Text]

  type JsonTypeMapper[T] = TypeMapper[T, MediaType.Json]
  type RequiredJsonTypeMapper[T] = RequiredTypeMapper[T, MediaType.Json]

  implicit val stringTextTypeMapper: RequiredTextTypeMapper[String] = textTypeMapper[String](identity, Schema.SString)
  implicit val shortTextTypeMapper: RequiredTextTypeMapper[Short] = textTypeMapper[Short](_.toShort, Schema.SInteger)
  implicit val intTextTypeMapper: RequiredTextTypeMapper[Int] = textTypeMapper[Int](_.toInt, Schema.SInteger)
  implicit val longTextTypeMapper: RequiredTextTypeMapper[Long] = textTypeMapper[Long](_.toLong, Schema.SInteger)
  implicit val floatTextTypeMapper: RequiredTextTypeMapper[Float] = textTypeMapper[Float](_.toFloat, Schema.SNumber)
  implicit val doubleTextTypeMapper: RequiredTextTypeMapper[Double] = textTypeMapper[Double](_.toDouble, Schema.SNumber)
  implicit val booleanTextTypeMapper: RequiredTextTypeMapper[Boolean] = textTypeMapper[Boolean](_.toBoolean, Schema.SBoolean)

  private def textTypeMapper[T](parse: String => T, _schema: Schema): RequiredTypeMapper[T, MediaType.Text] =
    new RequiredTextTypeMapper[T] {
      override def toString(t: T): String = t.toString
      override def fromString(s: String): DecodeResult[T] =
        try Value(parse(s))
        catch {
          case e: Exception => Error(s, e, "Cannot parse")
        }
      override def schema: Schema = _schema
      override def mediaType = MediaType.Text()
    }

  implicit def optionalTypeMapper[T, M <: MediaType](implicit tm: RequiredTypeMapper[T, M]): TypeMapper[Option[T], M] =
    new TypeMapper[Option[T], M] {
      override def toOptionalString(t: Option[T]): Option[String] = t.map(tm.toString)
      override def fromOptionalString(s: Option[String]): DecodeResult[Option[T]] = s match {
        case None => DecodeResult.Value(None)
        case Some(ss) =>
          tm.fromString(ss) match {
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
