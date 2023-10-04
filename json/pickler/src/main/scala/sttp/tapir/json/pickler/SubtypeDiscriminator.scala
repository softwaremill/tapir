package sttp.tapir.json.pickler

import sttp.tapir.Validator

private[pickler] sealed trait SubtypeDiscriminator[T]

/** Describes non-standard encoding/decoding for subtypes in sealed hierarchies. Allows specifying an extractor function, for example to
  * read subtype discriminator from a field. Requires also mapping in the opposite direction, to specify how to read particular
  * discriminator values into concrete subtype picklers.
  */
private[pickler] trait CustomSubtypeDiscriminator[T] extends SubtypeDiscriminator[T]:
  type V
  def extractor: T => V
  def asString: V => String
  def write(t: T): String = asString(extractor(t))
  def mapping: Seq[(V, Pickler[_ <: T])]

  // to integrate with uPickle where at some point all we have is Any
  def writeUnsafe(t: Any): String = asString(extractor(t.asInstanceOf[T]))

private[pickler] case class DefaultSubtypeDiscriminator[T]() extends SubtypeDiscriminator[T]
