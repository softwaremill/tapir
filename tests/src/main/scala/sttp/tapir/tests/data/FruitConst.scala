package sttp.tapir.tests.data

import sttp.tapir.Schema

case class FruitConst(fruitConstName: String)

object FruitConst {
  implicit val schemw: Schema[FruitConst] = Schema
    .derived[FruitConst]
    .modifyUnsafe[String]("fruitConstName")(s => s.copy(const = Some("Red Apple Const", Some("Red Apple Const"))))
}
