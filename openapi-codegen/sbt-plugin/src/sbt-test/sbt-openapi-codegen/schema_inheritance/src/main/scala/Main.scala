import sttp.tapir.generated.core.TapirGeneratedEndpoints._
import sttp.tapir.generated.v1.TapirGeneratedEndpoints.{Order => V1Order, createOrder}
import sttp.tapir.generated.v2.TapirGeneratedEndpoints.{Pet => V2Pet, listPets}

import sttp.tapir.generated.reuse.all.TapirGeneratedEndpoints.{Order => ReuseAllOrder, Pet => ReuseAllPet}
import sttp.tapir.generated.reuse.enum.TapirGeneratedEndpoints.{Status => ReuseEnumStatus}
import sttp.tapir.generated.reuse.nested.TapirGeneratedEndpoints.{Address => ReuseAddress, Person => ReusePerson}
import sttp.tapir.generated.reuse.oneof.TapirGeneratedEndpoints.{Animal => ReuseAnimal, Dog => ReuseDog}
import sttp.tapir.generated.reuse.map.TapirGeneratedEndpoints.{TagMap => ReuseTagMap}
import sttp.tapir.generated.reuse.array.TapirGeneratedEndpoints.{IdList => ReuseIdList}
import sttp.tapir.generated.reuse.alias.TapirGeneratedEndpoints.{PetName => ReusePetName}
import sttp.tapir.generated.reuse.allof_pet.TapirGeneratedEndpoints.{Pet => ReuseAllOfPet}
import sttp.tapir.generated.transitive.reuse.TapirGeneratedEndpoints.{Order => TransitiveReuseOrder, Pet => TransitiveReusePet}

import sttp.tapir.generated.chain.mid.TapirGeneratedEndpoints.{LegacyPet => ChainMidLegacyPet}
import sttp.tapir.generated.chain.leaf.TapirGeneratedEndpoints.{LegacyPet => ChainLeafLegacyPet}

import sttp.tapir.generated.shared.a.TapirGeneratedEndpoints.{Holder => SharedHolderA, Widget => SharedWidgetA}
import sttp.tapir.generated.shared.b.TapirGeneratedEndpoints.{Container => SharedContainerB, Widget => SharedWidgetB}

import sttp.tapir.generated.inline.pet.TapirGeneratedEndpoints.{Pet => InlinePet}

/** Positive compile-time checks: reuse assignments that must typecheck when aliasing works. */
object Main extends App {
  CompileAssertions.assertAll()

  val corePet: Pet = Pet("Rex", Some("dog"))
  val coreOrder: Order = Order(1L, corePet)
  val coreAddress: Address = Address("London", None)
  val corePerson: Person = Person("Alice", Some(coreAddress))
  val coreStatus: Status = Status.ACTIVE
  // discriminator `kind` is fixed on oneOf variants — not a constructor argument
  val coreDog: Dog = Dog(barks = true)
  val coreCat: Cat = Cat(lives = 9)
  val coreAnimal: Animal = coreDog
  val coreTagMap: TagMap = Map("a" -> "b")
  val coreIdList: IdList = List(1L, 2L)
  val corePetName: PetName = "fluffy"
  val coreWidget: Widget = Widget("w1", Some("label"))

  val v2Pet: V2Pet = V2Pet("Rex", Some("dog"))
  val v1Order: V1Order = V1Order(1L, v2Pet)

  val reuseAllPet: ReuseAllPet = corePet
  val reuseAllOrder: ReuseAllOrder = coreOrder
  val reuseEnumStatus: ReuseEnumStatus = coreStatus
  val reusePerson: ReusePerson = corePerson
  val reuseAddress: ReuseAddress = coreAddress
  val reuseAnimal: ReuseAnimal = coreAnimal
  val reuseDog: ReuseDog = coreDog
  val reuseTagMap: ReuseTagMap = coreTagMap
  val reuseIdList: ReuseIdList = coreIdList
  val reusePetName: ReusePetName = corePetName
  val reuseAllOfPet: ReuseAllOfPet = corePet
  val transitiveReuseOrder: TransitiveReuseOrder = coreOrder
  val transitiveReusePet: TransitiveReusePet = corePet

  val chainMidPet: ChainMidLegacyPet = ChainMidLegacyPet("old", Some("cat"))
  val chainLeafPet: ChainLeafLegacyPet = chainMidPet

  val sharedWidgetA: SharedWidgetA = coreWidget
  val sharedWidgetB: SharedWidgetB = coreWidget
  val crossConsumerWidget: SharedWidgetB = sharedWidgetA
  val holderA: SharedHolderA = SharedHolderA(coreWidget)
  val containerB: SharedContainerB = SharedContainerB(sharedWidgetA)

  val inlinePet: InlinePet = corePet

  println(
    Seq(
      createOrder.show,
      listPets.show,
      coreOrder,
      v1Order,
      reuseAllOrder,
      coreDog,
      coreCat
    ).mkString("\n")
  )
}
