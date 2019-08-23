package tapir

import java.io.File
import java.math.{BigDecimal => JBigDecimal}
import java.nio.ByteBuffer
import java.nio.file.Path
import java.time._
import java.util.{Date, UUID}

import tapir.generic.ValidateMagnoliaDerivation

trait Validator[T] { outer =>
  def validate(t: T): Boolean
  def map[TT](g: TT => T): Validator[TT] = (t: TT) => {
    outer.validate(g(t))
  }
}

object Validator extends ValidateMagnoliaDerivation {
  def passing[T]: Validator[T] = (t: T) => true
  def rejecting[T]: Validator[T] = (t: T) => false

  // These instances are needed only for FormCodedDerivation and probably for MultipartCodecDerivation, because magnolia's
  // fallback method doesn't work when called from another macro.
  // All other ways of obtaining codec instances (when there is no validator for given `T`)
  // will use magnolia's fallback method to provide generic passing validator
  implicit val validatorForString: Validator[String] = Validator.passing
  implicit val validatorForByte: Validator[Byte] = Validator.passing
  implicit val validatorForShort: Validator[Short] = Validator.passing
  implicit val validatorForInt: Validator[Int] = Validator.passing
  implicit val validatorForLong: Validator[Long] = Validator.passing
  implicit val validatorForFloat: Validator[Float] = Validator.passing
  implicit val validatorForDouble: Validator[Double] = Validator.passing
  implicit val validatorForBoolean: Validator[Boolean] = Validator.passing
  implicit val validatorForFile: Validator[File] = Validator.passing
  implicit val validatorForPath: Validator[Path] = Validator.passing
  implicit val validatorForByteArray: Validator[Array[Byte]] = Validator.passing
  implicit val validatorForByteBuffer: Validator[ByteBuffer] = Validator.passing
  implicit val validatorForInstant: Validator[Instant] = Validator.passing
  implicit val validatorForZonedDateTime: Validator[ZonedDateTime] = Validator.passing
  implicit val validatorForOffsetDateTime: Validator[OffsetDateTime] = Validator.passing
  implicit val validatorForDate: Validator[Date] = Validator.passing
  implicit val validatorForLocalDateTime: Validator[LocalDateTime] = Validator.passing
  implicit val validatorForLocalDate: Validator[LocalDate] = Validator.passing
  implicit val validatorForUUID: Validator[UUID] = Validator.passing
  implicit val validatorForBigDecimal: Validator[BigDecimal] = Validator.passing
  implicit val validatorForJBigDecimal: Validator[JBigDecimal] = Validator.passing

  implicit def validatorForOption[T: Validator]: Validator[Option[T]] = (t: Option[T]) => {
    t.forall(implicitly[Validator[T]].validate)
  }

  implicit def validatorForArray[T: Validator]: Validator[Array[T]] = new Validator[Array[T]] {
    override def validate(t: Array[T]): Boolean = {
      t.forall(implicitly[Validator[T]].validate)
    }
  }
  implicit def validatorForIterable[T: Validator, C[W] <: Iterable[W]]: Validator[C[T]] = new Validator[C[T]] {
    override def validate(t: C[T]): Boolean = {
      t.forall(implicitly[Validator[T]].validate)
    }
  }
}

case class ProductValidator[T](fields: Map[String, FieldValidator[T]]) extends Validator[T] {
  override def validate(t: T): Boolean = {
    fields.values.forall { f =>
      f.validator.validate(f.get(t))
    }
  }
}

trait FieldValidator[T] {
  type fType
  def get(t: T): fType
  def validator: Validator[fType]
}

case class ValueValidator[T](constraints: List[Constraint[T]]) extends Validator[T] {
  override def validate(t: T): Boolean = constraints.forall(_.check(t))
}
