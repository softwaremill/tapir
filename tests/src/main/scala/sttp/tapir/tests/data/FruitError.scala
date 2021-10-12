package sttp.tapir.tests.data

case class FruitError(msg: String, code: Int) extends RuntimeException
