package sttp.tapir

package object model {

  /** Used to lookup codecs which split/combine values using a comma. */
  type CommaSeparated[T] = Delimited[",", T]
}
