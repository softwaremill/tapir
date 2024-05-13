package sttp.tapir.tests.data

import sttp.tapir.Schema

case class FruitConst(fruitType: String)

object FruitConst {
  implicit val schemw: Schema[FruitConst] = Schema
    .derived[FruitConst]
    .modifyUnsafe[String]("fruitType")(s => s.copy(const = Some(("Golden Delicious", Some("Golden Delicious")))))
}
