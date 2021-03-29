package sttp.tapir

import sttp.tapir.SchemaType.SProductField

object TestUtil {
  def field[T, U](_name: FieldName, _schema: Schema[U]): SchemaType.SProductField[T] = SProductField[T, U](_name, _schema, _ => None)
}
