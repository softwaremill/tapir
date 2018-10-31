package sapi

import io.circe.{Decoder, Encoder}

// we could encode T to an SString/SInt/SObject/... AST, but that would require allocations.
// Instead, we can go directly to Json

// Schema: name (type name); what about recurrence?

trait TypeMapper[T] { // TODO: pull format as a second type parameter
  def encodeToOptional[U: Format](t: T): Option[U]
  def decodeFromOptional[U: Format](u: Option[U]): DecodeResult[T]
  def isOptional: Boolean
  def schema: Schema
}

trait RequiredTypeMapper[T] extends TypeMapper[T] { // type mapper can provide restrictions on what kind of formats it might use? Basic/Extended
  def encode[U: Format](t: T): U
  def decode[U: Format](u: U): DecodeResult[T]

  override def encodeToOptional[U: Format](t: T): Option[U] = Some(encode(t))
  override def decodeFromOptional[U: Format](u: Option[U]): DecodeResult[T] = u match {
    case None     => DecodeResult.Missing
    case Some(uu) => decode(uu)
  }

  def isOptional: Boolean = false
}

object TypeMapper {
  implicit val stringTypeMapper: RequiredTypeMapper[String] = new RequiredTypeMapper[String] {
    override def encode[U](t: String)(implicit f: Format[U]): U = f.encodeString(t)
    override def decode[U](u: U)(implicit f: Format[U]): DecodeResult[String] = f.decodeString(u)
    override def schema: Schema = Schema.SString
  }
  implicit val intTypeMapper: RequiredTypeMapper[Int] = new RequiredTypeMapper[Int] {
    override def encode[U](t: Int)(implicit f: Format[U]): U = f.encodeInt(t)
    override def decode[U](u: U)(implicit f: Format[U]): DecodeResult[Int] = f.decodeInt(u)
    override def schema: Schema = Schema.SInt
  }

  implicit val unitTypeMapper: TypeMapper[Unit] = new TypeMapper[Unit] {
    override def encodeToOptional[U: Format](t: Unit): Option[U] = None
    override def decodeFromOptional[U: Format](u: Option[U]): DecodeResult[Unit] = DecodeResult.Value(())
    override def isOptional: Boolean = true
    override def schema: Schema = Schema.SEmpty
  }

  implicit def optionalTypeMapper[T](implicit tm: RequiredTypeMapper[T]): TypeMapper[Option[T]] = new TypeMapper[Option[T]] {
    override def encodeToOptional[U: Format](t: Option[T]): Option[U] = t.map(tm.encode[U])
    override def decodeFromOptional[U: Format](u: Option[U]): DecodeResult[Option[T]] = u match {
      case None => DecodeResult.Value(None)
      case Some(uu) =>
        tm.decode(uu) match {
          case DecodeResult.Value(v)  => DecodeResult.Value(Some(v))
          case DecodeResult.Missing   => DecodeResult.Missing
          case de: DecodeResult.Error => de
        }
    }
    override def isOptional: Boolean = true
    override def schema: Schema = tm.schema
  }

  implicit def objectTypeMapper[T: Encoder: Decoder: SchemaFor]: TypeMapper[T] = new RequiredTypeMapper[T] {
    override def encode[U: Format](t: T): U = ???
    override def decode[U: Format](u: U): DecodeResult[T] = ???
    override def schema: Schema = implicitly[SchemaFor[T]].schema
  }
}

/*
Format[U]: data serialization format - how data is represented as text, with U as the intermediate format
TypeMapper[T]: how a T maps to a given format's internal representation
 */

import shapeless._

trait ObjectTypeMapper[T] {
  def serialize[U](t: T, format: Format[U]): U
  /*
   for each field: get the value of the field, serialize, pass the name -> serialized value map to format


   */

  def deserialize[U](u: U, format: Format[U]): Option[T]
  /*
  deserialize u into Map[String, U]
 */
}

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
