package tapir

import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import tapir.DecodeResult._

trait TypeMapper[T, M <: MediaType] {
  def toOptionalString(t: T): Option[String]
  def fromOptionalString(s: Option[String]): DecodeResult[T]
  def isOptional: Boolean
  def schema: Schema
  def mediaType: M
}

trait RequiredTypeMapper[T, M <: MediaType] extends TypeMapper[T, M] { // type mapper can provide restrictions on what kind of formats it might use? Basic/Extended
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

  implicit val stringTextTypeMapper: RequiredTextTypeMapper[String] = new RequiredTextTypeMapper[String] {
    override def toString(t: String): String = t
    override def fromString(s: String): DecodeResult[String] = Value(s)
    override def schema: Schema = Schema.SString
    override def mediaType = MediaType.Text()
  }
  implicit val intTextTypeMapper: RequiredTextTypeMapper[Int] = new RequiredTextTypeMapper[Int] {
    override def toString(t: Int): String = t.toString
    override def fromString(s: String): DecodeResult[Int] =
      try Value(s.toInt)
      catch {
        case e: Exception => Error(s, e, "Cannot parse integer")
      }
    override def schema: Schema = Schema.SInt
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

  implicit val stringJsonTypeMapper: RequiredJsonTypeMapper[String] = new RequiredJsonTypeMapper[String] {
    override def toString(t: String): String = t.asJson.noSpaces
    override def fromString(s: String): DecodeResult[String] = Value(s)
    override def schema: Schema = Schema.SString
    override def mediaType = MediaType.Json()
  }
  implicit val intJsonTypeMapper: RequiredJsonTypeMapper[Int] = new RequiredJsonTypeMapper[Int] {
    override def toString(t: Int): String = t.asJson.noSpaces
    override def fromString(s: String): DecodeResult[Int] =
      try Value(s.toInt)
      catch {
        case e: Exception => Error(s, e, "Cannot parse integer")
      }
    override def schema: Schema = Schema.SInt
    override def mediaType = MediaType.Json()
  }
  implicit def objectTypeMapper[T: Encoder: Decoder: SchemaFor]: RequiredJsonTypeMapper[T] = new RequiredJsonTypeMapper[T] {
    override def toString(t: T): String = t.asJson.noSpaces
    override def fromString(s: String): DecodeResult[T] = io.circe.parser.decode[T](s) match {
      case Left(error) => Error(s, error, error.getMessage)
      case Right(v)    => Value(v)
    }
    override def schema: Schema = implicitly[SchemaFor[T]].schema
    override def mediaType = MediaType.Json()
  }
}

/*
Format[U]: data serialization format - how data is represented as text, with U as the intermediate format
TypeMapper[T]: how a T maps to a given format's internal representation
 */

/*
case class User(age: Int, address: Address)

# ShapelessObjectMapper -> implements encode/decode given:

Fields = Int :: Address
FieldNames: Fields ~> String ('age :: 'address)
FieldTMs: Fields ~> TypeMapper[_] (TM[Int] :: TM[Address])

get: T -> Fields
set: Fields -> T

 */

/*

Basic type mappers: ONLY string, int, double, boolean, object, sequence
Derived type mappers: by bi-mapping

For each parameter, body we need to know:
- the schema: how it maps to a string/number/boolean/object/array combination + is optional
- how to serialize:
  - if content type text -> string
  - if content type json -> json ...
  - string/numbers/booleans serialized directly
  - objects (schema: map field name -> field value schema)
    for each field a getter T -> U, serializing recursively & combining using the content type
- how to deserialize from a string:
  - string/numbers/booleans deserialized directly
  - objects:
    deserialize each field, reconstruct object given a List[Any] -> T

 */
