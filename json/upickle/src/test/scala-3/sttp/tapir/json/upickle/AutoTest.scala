package sttp.tapir.json.upickle

import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpecLike
import auto.*
import upickle.implicits.key

class AutoTest extends AnyFlatSpecLike with Matchers {

  val tapirPickle = new TapirPickle(CodecConfiguration(Snake, AsOrdinalInt, DiscriminatorField("$type")))

  object TestFixture:
    enum ColorEnum(val rgb: Int) derives tapirPickle.ReadWriter:
      @key("#FF0000") case Red extends ColorEnum(0xff0000)
      case Green extends ColorEnum(0x00ff00)
      case Blue extends ColorEnum(0x0000ff)
      @key("myMix") case Mix(mix: Int) extends ColorEnum(mix)
      case Unknown(mix: Int) extends ColorEnum(0x000000)

    inline def mapName(inline value: String): String = s"${value}_transformed"

    enum SimpleEnum derives tapirPickle.ReadWriter:

      case SimpleCaseOne extends SimpleEnum
      @upickle.implicits.key("2") case SimpleCaseTwo extends SimpleEnum

    sealed trait AnimalKind
    object AnimalKind {
      implicit val rw: tapirPickle.ReadWriter[AnimalKind] =
        tapirPickle.ReadWriter.merge(tapirPickle.macroRW[Mammal], tapirPickle.macroRW[Bird.type])
    }
    @upickle.implicits.key("custom_mammal")
    case class Mammal() extends AnimalKind {}
    case object Bird extends AnimalKind {}

    case class DogAge(years: Int)
    case class DogOwner(name: String, ownerAge: Int)
    case class Dog(name: String, dogAge: DogAge, dogOwner: DogOwner, kind: AnimalKind, simpleEnum: SimpleEnum, complexEnum: ColorEnum)

    // given animalKindRw: TapirPickle.ReadWriter[AnimalKind] = TapirPickle.ReadWriter.derived[AnimalKind]

  case class SimpleCaseClass(aField: Int, b: Int)
  case class ClassWithDefaultMember(a: Int, b: SimpleCaseClass = SimpleCaseClass(3, 4))

  it should "work" in {
    val autoIntCodec = implicitly[tapirPickle.ReadWriter[Int]]

    import TestFixture.*
    given simpleCCRW: tapirPickle.ReadWriter[SimpleCaseClass] = tapirPickle.deriveRW
    given dogAgeR: tapirPickle.ReadWriter[DogAge] = tapirPickle.deriveRW
    given ownerRw: tapirPickle.ReadWriter[DogOwner] = tapirPickle.deriveRW
    given dogRw: tapirPickle.ReadWriter[Dog] = tapirPickle.deriveRW
    val dogJsonStr = tapirPickle.write(
      Dog("Bob", DogAge(33), DogOwner(name = "Anthony", ownerAge = 39), Mammal(), SimpleEnum.SimpleCaseTwo, ColorEnum.Mix(15))
    )
    println(s"Hello, $dogJsonStr")
    val intJsonStr = tapirPickle.write(3)
    val cc = tapirPickle.read[Dog](
      """{"name":"Bob","dog_age":{"years":33},"dog_owner":{"name":"Anthony","owner_age":39}, "kind":{"mammal_param":44}, "simple_enum":"SimpleCaseTwo"}"""
    )
    println(cc)

  }
}
