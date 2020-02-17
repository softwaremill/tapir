package sttp.tapir

import java.io.{File, InputStream}
import java.math.{BigDecimal => JBigDecimal}
import java.nio.ByteBuffer
import java.nio.file.Path
import java.time._
import java.util.{Date, UUID}

import sttp.model.Part
import sttp.tapir.SchemaType._
import sttp.tapir.generic.internal.OneOfMacro.oneOfMacro
import sttp.tapir.generic.internal.{SchemaMagnoliaDerivation, SchemaMapMacro}
import sttp.tapir.generic.Derived
import sttp.tapir.internal.ModifySchemaMacro

/**
  * Describes the shape of the low-level, "raw" representation of type `T`.
  * @param format The name of the format of the low-level representation of `T`.
  */
case class Schema[T](
    schemaType: SchemaType,
    isOptional: Boolean = false,
    description: Option[String] = None,
    format: Option[String] = None,
    deprecated: Boolean = false
) {

  /**
    * Returns an optional version of this schema, with `isOptional` set to true.
    */
  def asOptional[U]: Schema[U] = copy(isOptional = true)

  /**
    * Returns a collection version of this schema, with the schema type wrapped in [[SArray]].
    * Also, sets `isOptional` to true as the collection might be empty.
    * Also, sets 'format' to None. Formats are only applicable to the array elements, not to the array as a whole.
    */
  def asArrayElement[U]: Schema[U] = copy(isOptional = true, schemaType = SArray(this), format = None)

  def description(d: String): Schema[T] = copy(description = Some(d))

  def format(f: String): Schema[T] = copy(format = Some(f))

  def deprecated(d: Boolean): Schema[T] = copy(deprecated = d)

  def show: String = s"schema is $schemaType${if (isOptional) " (optional)" else ""}"

  def modifyUnsafe[U](fields: String*)(modify: Schema[U] => Schema[U]): Schema[T] = modifyAtPath(fields.toList, modify)

  def modify[U](path: T => U)(modification: Schema[U] => Schema[U]): Schema[T] = macro ModifySchemaMacro.modifyMacro[T, U]

  private def modifyAtPath[U](fieldPath: List[String], modify: Schema[U] => Schema[U]): Schema[T] = fieldPath match {
    case Nil => modify(this.asInstanceOf[Schema[U]]).asInstanceOf[Schema[T]] // we don't have type-polymorphic functions
    case f :: fs =>
      val schemaType2 = schemaType match {
        case SArray(element) if f == Schema.ModifyCollectionElements => SArray(element.modifyAtPath(fs, modify))
        case s @ SProduct(_, fields) =>
          s.copy(fields = fields.toList.map {
            case field @ (fieldName, fieldSchema) =>
              if (fieldName == f) (fieldName, fieldSchema.modifyAtPath(fs, modify)) else field
          })
        case s @ SOpenProduct(_, valueSchema) if f == Schema.ModifyCollectionElements =>
          s.copy(valueSchema = valueSchema.modifyAtPath(fs, modify))
        case s @ SCoproduct(_, schemas, _) => s.copy(schemas = schemas.map(_.modifyAtPath(fieldPath, modify)))
        case _                             => schemaType
      }
      copy(schemaType = schemaType2)
  }
}

object Schema extends SchemaMagnoliaDerivation with LowPrioritySchema {
  val ModifyCollectionElements = "each"

  implicit val schemaForString: Schema[String] = Schema(SString)
  implicit val schemaForByte: Schema[Byte] = Schema(SInteger)
  implicit val schemaForShort: Schema[Short] = Schema(SInteger)
  implicit val schemaForInt: Schema[Int] = Schema(SInteger)
  implicit val schemaForLong: Schema[Long] = Schema(SInteger).format("int64")
  implicit val schemaForFloat: Schema[Float] = Schema(SNumber).format("float")
  implicit val schemaForDouble: Schema[Double] = Schema(SNumber).format("double")
  implicit val schemaForBoolean: Schema[Boolean] = Schema(SBoolean)
  implicit val schemaForFile: Schema[File] = Schema(SBinary)
  implicit val schemaForPath: Schema[Path] = Schema(SBinary)
  implicit val schemaForByteArray: Schema[Array[Byte]] = Schema(SBinary)
  implicit val schemaForByteBuffer: Schema[ByteBuffer] = Schema(SBinary)
  implicit val schemaForInputStream: Schema[InputStream] = Schema(SBinary)
  implicit val schemaForInstant: Schema[Instant] = Schema(SDateTime)
  implicit val schemaForZonedDateTime: Schema[ZonedDateTime] = Schema(SDateTime)
  implicit val schemaForOffsetDateTime: Schema[OffsetDateTime] = Schema(SDateTime)
  implicit val schemaForDate: Schema[Date] = Schema(SDateTime)
  implicit val schemaForLocalDateTime: Schema[LocalDateTime] = Schema(SDateTime)
  implicit val schemaForLocalDate: Schema[LocalDate] = Schema(SDate)
  implicit val schemaForZoneOffset: Schema[ZoneOffset] = Schema(SString)
  implicit val schemaForJavaDuration: Schema[Duration] = Schema(SString)
  implicit val schemaForLocalTime: Schema[LocalTime] = Schema(SString)
  implicit val schemaForOffsetTime: Schema[OffsetTime] = Schema(SString)
  implicit val schemaForScalaDuration: Schema[scala.concurrent.duration.Duration] = Schema(SString)
  implicit val schemaForUUID: Schema[UUID] = Schema(SString)
  implicit val schemaForBigDecimal: Schema[BigDecimal] = Schema(SString)
  implicit val schemaForJBigDecimal: Schema[JBigDecimal] = Schema(SString)

  implicit def schemaForOption[T: Schema]: Schema[Option[T]] = implicitly[Schema[T]].asOptional

  implicit def schemaForArray[T: Schema]: Schema[Array[T]] = implicitly[Schema[T]].asArrayElement

  implicit def schemaForIterable[T: Schema, C[_] <: Iterable[_]]: Schema[C[T]] = implicitly[Schema[T]].asArrayElement

  implicit def schemaForPart[T: Schema]: Schema[Part[T]] = Schema[Part[T]](implicitly[Schema[T]].schemaType)

  implicit def schemaForMap[V: Schema]: Schema[Map[String, V]] = macro SchemaMapMacro.schemaForMap[Map[String, V], V]

  def oneOf[E, V](extractor: E => V, asString: V => String)(mapping: (V, Schema[_])*): Schema[E] = macro oneOfMacro[E, V]
}

trait LowPrioritySchema {
  implicit def derivedSchema[T](implicit derived: Derived[Schema[T]]): Schema[T] = derived.value
}
