package sttp.tapir.model

/** Used to lookup codecs which split/combine values using the given `DELIMITER`.
  *
  * @see
  *   [[sttp.tapir.Codec.delimited]]
  *
  * @see
  *   [[CommaSeparated]]
  */
case class Delimited[DELIMITER <: String, T](values: List[T])
