package sttp.tapir.tests.data

trait FruitErrorDetail
object FruitErrorDetail {
  case class NameTooShort(length: Int) extends FruitErrorDetail
  case class Unknown(availableFruit: List[String]) extends FruitErrorDetail

  trait HarvestError extends FruitErrorDetail
  case class NotYetGrown(availableInDays: Int) extends HarvestError
  case class AlreadyPicked(name: String) extends HarvestError
}
