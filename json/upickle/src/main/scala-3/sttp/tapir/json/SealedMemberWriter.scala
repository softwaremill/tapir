package sttp.tapir.json

import sttp.tapir.Validator

sealed trait SubtypeDiscriminator[T]

trait CustomSubtypeDiscriminator[T] extends SubtypeDiscriminator[T]:
  type V
  def extractor: T => V
  def asString: V => String
  def write(t: T): String = asString(extractor(t))
  def mapping: Seq[(V, Pickler[_ <: T])]

  // to integrate with uPickle where at some point all we have is Any
  def writeUnsafe(t: Any): String = asString(extractor(t.asInstanceOf[T]))

case class EnumValueDiscriminator[T](encode: T => String, validator: Validator.Enumeration[T]) extends SubtypeDiscriminator[T]

case class DefaultSubtypeDiscriminator[T]() extends SubtypeDiscriminator[T]