package sttp.tapir.tests

import com.softwaremill.tagging.@@
import sttp.tapir.tests.BasketOfFruits._

case class BasketOfFruits(fruits: ValidatedList[ValidFruitAmount])

object BasketOfFruits {
  type ValidatedList[A] = List[A] @@ BasketOfFruits
}
