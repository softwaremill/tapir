package tapir

import java.io.{File, InputStream}
import java.math.{BigDecimal => JBigDecimal}
import java.nio.ByteBuffer
import java.nio.file.Path
import java.time._
import java.util.{Date, UUID}

import sttp.model.Part
import tapir.SchemaType._
import tapir.generic.OneOfMacro.oneOfMacro
import tapir.generic.SchemaMagnoliaDerivation

/**
  * Describes the shape of the low-level, "raw" representation of type `T`.
  * @param format The name of the format of the low-level representation of `T`.
  */
case class Schema[T](
    schemaType: SchemaType,
    isOptional: Boolean = false,
    description: Option[String] = None,
    format: Option[String] = None
) {
  /**
    * Returns an optional version of this schema, with `isOptional` set to true.
    */
  def asOptional[U]: Schema[U] = copy(isOptional = true)

  /**
    * Returns a collection version of this schema, with the schema type wrapped in [[SArray]].
    * Also, sets `isOptional` to true as the collection might be empty.
    */
  def asArrayElement[U]: Schema[U] = copy(isOptional = true, schemaType = SArray(this))

  def description(d: String): Schema[T] = copy(description = Some(d))

  def format(f: String): Schema[T] = copy(format = Some(f))

  def show: String = s"schema is $schemaType${if (isOptional) " (optional)" else ""}"
}

object Schema extends SchemaMagnoliaDerivation {
  implicit val schemaForString: Schema[String] = Schema(SString)
  implicit val schemaForByte: Schema[Byte] = Schema(SInteger)
  implicit val schemaForShort: Schema[Short] = Schema(SInteger)
  implicit val schemaForInt: Schema[Int] = Schema(SInteger)
  implicit val schemaForLong: Schema[Long] = Schema(SInteger)
  implicit val schemaForFloat: Schema[Float] = Schema(SNumber)
  implicit val schemaForDouble: Schema[Double] = Schema(SNumber)
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
  implicit val schemaForScalaDuration: Schema[scala.concurrent.duration.Duration] = Schema(SString)
  implicit val schemaForUUID: Schema[UUID] = Schema(SString)
  implicit val schemaForBigDecimal: Schema[BigDecimal] = Schema(SString)
  implicit val schemaForJBigDecimal: Schema[JBigDecimal] = Schema(SString)

  implicit def schemaForOption[T: Schema]: Schema[Option[T]] = implicitly[Schema[T]].asOptional

  implicit def schemaForArray[T: Schema]: Schema[Array[T]] = implicitly[Schema[T]].asArrayElement

  implicit def schemaForIterable[T: Schema, C[_] <: Iterable[_]]: Schema[C[T]] = implicitly[Schema[T]].asArrayElement

  implicit def schemaForPart[T: Schema]: Schema[Part[T]] = Schema[Part[T]](implicitly[Schema[T]].schemaType)

  implicit def schemaForMap[V: Schema]: Schema[Map[String, V]] = macro generic.SchemaMapMacro.schemaForMap[Map[String, V], V]

  def oneOf[E, V](extractor: E => V, asString: V => String)(mapping: (V, Schema[_])*): Schema[E] = macro oneOfMacro[E, V]
}
