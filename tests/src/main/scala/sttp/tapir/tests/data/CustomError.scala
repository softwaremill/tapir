package sttp.tapir.tests.data

sealed trait CustomError
object CustomError {
  case class Default(msg: String) extends CustomError
  object NotFound extends CustomError
}
