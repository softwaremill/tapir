package tapir
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Path

import tapir.Schema._
import tapir.generic.SchemaForMagnoliaDerivation

trait SchemaFor[T] {
  def schema: Schema
  def isOptional: Boolean = false
  def show: String = s"schema is $schema"
}
object SchemaFor extends SchemaForMagnoliaDerivation {
  implicit case object SchemaForString extends SchemaFor[String] {
    override val schema: Schema = SString
  }
  implicit case object SchemaForShort extends SchemaFor[Short] {
    override val schema: Schema = SInteger
  }
  implicit case object SchemaForInt extends SchemaFor[Int] {
    override val schema: Schema = SInteger
  }
  implicit case object SchemaForLong extends SchemaFor[Long] {
    override val schema: Schema = SInteger
  }
  implicit case object SchemaForFloat extends SchemaFor[Float] {
    override val schema: Schema = SNumber
  }
  implicit case object SchemaForDouble extends SchemaFor[Double] {
    override val schema: Schema = SNumber
  }
  implicit case object SchemaForBoolean extends SchemaFor[Boolean] {
    override val schema: Schema = SBoolean
  }
  implicit case object SchemaForFile extends SchemaFor[File] {
    override val schema: Schema = SBinary
  }
  implicit case object SchemaForPath extends SchemaFor[Path] {
    override val schema: Schema = SBinary
  }
  implicit case object SchemaForByteArray extends SchemaFor[Array[Byte]] {
    override val schema: Schema = SBinary
  }
  implicit case object SchemaForByteBuffer extends SchemaFor[ByteBuffer] {
    override val schema: Schema = SBinary
  }
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
}
