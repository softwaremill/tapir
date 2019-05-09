package tapir

import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Path
import java.time._
import java.util.{Date, UUID}

import tapir.Schema._
import tapir.generic.OneOfMacro.oneOfMacro
import tapir.generic.SchemaForMagnoliaDerivation
import tapir.model.Part

trait SchemaFor[T] {
  def schema: Schema
  def isOptional: Boolean = false
  def show: String = s"schema is $schema"
}
object SchemaFor extends SchemaForMagnoliaDerivation {
  def apply[T](s: Schema): SchemaFor[T] = new SchemaFor[T] {
    override def schema: Schema = s
  }

  implicit val schemaForString: SchemaFor[String] = SchemaFor(SString)
  implicit val schemaForShort: SchemaFor[Short] = SchemaFor(SInteger)
  implicit val schemaForInt: SchemaFor[Int] = SchemaFor(SInteger)
  implicit val schemaForLong: SchemaFor[Long] = SchemaFor(SInteger)
  implicit val schemaForFloat: SchemaFor[Float] = SchemaFor(SNumber)
  implicit val schemaForDouble: SchemaFor[Double] = SchemaFor(SNumber)
  implicit val schemaForBoolean: SchemaFor[Boolean] = SchemaFor(SBoolean)
  implicit val schemaForFile: SchemaFor[File] = SchemaFor(SBinary)
  implicit val schemaForPath: SchemaFor[Path] = SchemaFor(SBinary)
  implicit val schemaForByteArray: SchemaFor[Array[Byte]] = SchemaFor(SBinary)
  implicit val schemaForByteBuffer: SchemaFor[ByteBuffer] = SchemaFor(SBinary)
  implicit val schemaForInstant: SchemaFor[Instant] = SchemaFor(SDateTime)
  implicit val schemaForZonedDateTime: SchemaFor[ZonedDateTime] = SchemaFor(SDateTime)
  implicit val schemaForOffsetDateTime: SchemaFor[OffsetDateTime] = SchemaFor(SDateTime)
  implicit val schemaForDate: SchemaFor[Date] = SchemaFor(SDateTime)
  implicit val schemaForLocalDateTime: SchemaFor[LocalDateTime] = SchemaFor(SDateTime)
  implicit val schemaForLocalDate: SchemaFor[LocalDate] = SchemaFor(SDate)
  implicit val schemaForUUID: SchemaFor[UUID] = SchemaFor(SString)

  implicit def schemaForOption[T: SchemaFor]: SchemaFor[Option[T]] = new SchemaFor[Option[T]] {
    override def schema: Schema = implicitly[SchemaFor[T]].schema
    override def isOptional: Boolean = true
  }

  implicit def schemaForArray[T: SchemaFor]: SchemaFor[Array[T]] = new SchemaFor[Array[T]] {
    override def schema: Schema = SArray(implicitly[SchemaFor[T]].schema)
  }
  implicit def schemaForIterable[T: SchemaFor, C[_] <: Iterable[_]]: SchemaFor[C[T]] = new SchemaFor[C[T]] {
    override def schema: Schema = SArray(implicitly[SchemaFor[T]].schema)
  }

  implicit def schemaForPart[T: SchemaFor]: SchemaFor[Part[T]] = new SchemaFor[Part[T]] {
    override def schema: Schema = implicitly[SchemaFor[T]].schema
  }

  implicit def schemaForMap[V: SchemaFor]: SchemaFor[Map[String, V]] = SchemaFor(SProduct(SObjectInfo("Map"), List.empty, List.empty))

  def oneOf[E, V](extractor: E => V, asString: V => String)(mapping: (V, SchemaFor[_])*): SchemaFor[E] = macro oneOfMacro[E, V]
}
