package sttp.tapir

import sttp.tapir.SchemaType.ProductField

object TestUtil {
  def field[T, U](_name: FieldName, _schema: Schema[U]): SchemaType.ProductField[T] = new ProductField[T] {
    override type FieldType = U
    override def name: FieldName = _name
    override def get(t: T): Option[U] = None
    override def schema: Schema[U] = _schema
  }
}
