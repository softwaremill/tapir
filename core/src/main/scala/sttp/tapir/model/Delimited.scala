package sttp.tapir.model

/** Used to represent lists of values delimited with `DELIMITER`.
  *
  * @see
  *   [[sttp.tapir.Codec.delimited]]
  * @see
  *   [[sttp.tapir.Schema.schemaForDelimited]]
  * @see
  *   [[CommaSeparated]]
  */
case class Delimited[DELIMITER <: String, T](values: List[T])
