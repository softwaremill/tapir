package sttp.tapir.tests.data

import com.softwaremill.tagging.@@
import sttp.tapir.tests.data.BasketOfFruits._

case class BasketOfFruits(fruits: ValidatedList[ValidFruitAmount])

object BasketOfFruits {
  type ValidatedList[A] = List[A] @@ BasketOfFruits
}
