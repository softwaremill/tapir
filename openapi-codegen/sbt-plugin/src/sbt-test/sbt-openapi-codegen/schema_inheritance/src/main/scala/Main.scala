import sttp.tapir.generated.v1.TapirGeneratedEndpoints._
import sttp.tapir.generated.v2.TapirGeneratedEndpoints.Pet

object Main extends App {
  val pet: Pet = Pet("Rex", Some("dog"))
  val order: Order = Order(1L, pet)
  println(createOrder.show)
  println(sttp.tapir.generated.v2.TapirGeneratedEndpoints.listPets.show)
  println(order)
}
