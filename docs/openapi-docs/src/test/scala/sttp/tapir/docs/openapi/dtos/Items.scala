package sttp.tapir.docs.openapi.dtos

import sttp.tapir.{Schema, Validator}

final case class Items[A](items: List[A], total: Int)

object Items {
  implicit def schema[A: Schema]: Schema[Items[A]] = Schema.derived[Items[A]]
  implicit def validator[A: Schema]: Validator[Items[A]] = schema.validator
}
