package sttp.tapir.json.upickle

import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpecLike
import auto.*
import upickle.implicits.key

case class SimpleWithDefault(aField: String, bField: String = "8")
class AutoTest extends AnyFlatSpecLike with Matchers {

  given globalConfig: CodecConfiguration = CodecConfiguration(
    fieldNameCase = Snake,
    inheritanceReaderStrategy = InheritanceReaderStrategy.Default,
    discriminatorField = None
  )

  object TestFixture:
    enum ColorEnum(val rgb: Int):
      @key("#FF0000") case Red extends ColorEnum(0xff0000)
      case Green extends ColorEnum(0x00ff00)
      case Blue extends ColorEnum(0x0000ff)
      @key("myMix") case Mix(mix: Int) extends ColorEnum(mix)
      case Unknown(mix: Int) extends ColorEnum(0x000000)

    inline def mapName(inline value: String): String = s"${value}_transformed"

    enum SimpleEnum:

      case SimpleCaseOne extends SimpleEnum
      @upickle.implicits.key("2") case SimpleCaseTwo extends SimpleEnum

    case class DogAge(years: Int)
    case class DogOwner(name: String, ownerAge: Int)

    // given animalKindRw: TapirPickle.ReadWriter[AnimalKind] = TapirPickle.ReadWriter.derived[AnimalKind]

  import sttp.tapir.Schema.annotations._
  case class SimpleCaseClass(aField: Int, @encodedName("encoded_b_ann") b: Int)
  case class ClassWithDefaultMember(a: Int, b: SimpleCaseClass = SimpleCaseClass(3, 4))

  it should "work" in {

    import TestFixture.*
    val ccPickle: TapirPickle[SimpleCaseClass] = TapirPickle.deriveConfigured[SimpleCaseClass]
    given simpleCaseClassRw: ccPickle.ReadWriter[SimpleCaseClass] = ccPickle.macroRW

    val jsonStr = ccPickle.write(SimpleCaseClass(aField = 3, b = 17))
    println(s"$jsonStr")
  }
}
