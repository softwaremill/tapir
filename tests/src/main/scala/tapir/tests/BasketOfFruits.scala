package tapir.tests

import com.softwaremill.tagging.@@
import tapir.tests.BasketOfFruits._

case class BasketOfFruits(fruits: ValidatedList[ValidFruitAmount])

object BasketOfFruits {
  type ValidatedList[A] = List[A] @@ BasketOfFruits
}
