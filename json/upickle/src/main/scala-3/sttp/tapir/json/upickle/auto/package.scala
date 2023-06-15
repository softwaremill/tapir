package sttp.tapir.json.upickle

import upickle.AttributeTagged
import scala.deriving.Mirror
import scala.reflect.ClassTag
import scala.quoted._

package object auto {

  sealed trait AnimalKind
  case class Mammal(mammalParam: Int) extends AnimalKind
  case class Bird(birdParam: String) extends AnimalKind

  case class DogAge(years: Int)
  case class Dog(name: String, dogAge: DogAge)

  object TapirPickle extends AttributeTagged {
    def camelToSnake(s: String) = {
      s.replaceAll("([A-Z])", "#$1").split('#').map(_.toLowerCase).mkString("_")
    }
    def snakeToCamel(s: String) = {
      val res = s.split("_", -1).map(x => s"${x(0).toUpper}${x.drop(1)}").mkString
      s"${s(0).toLower}${res.drop(1)}"
    }

    override def objectAttributeKeyReadMap(s: CharSequence): CharSequence =
      snakeToCamel(s.toString)
    override def objectAttributeKeyWriteMap(s: CharSequence): CharSequence =
      camelToSnake(s.toString)

    override def objectTypeKeyReadMap(s: CharSequence): CharSequence =
      snakeToCamel(s.toString)
    override def objectTypeKeyWriteMap(s: CharSequence): CharSequence =
      camelToSnake(s.toString)

  }

  // given animalKindRw: TapirPickle.ReadWriter[AnimalKind] = TapirPickle.ReadWriter.derived[AnimalKind]
  given dogAgeR: TapirPickle.ReadWriter[DogAge] = TapirPickle.ReadWriter.derived[DogAge]
  given dogRw: TapirPickle.ReadWriter[Dog] = TapirPickle.ReadWriter.derived[Dog]

  import magnolia1.*
  import scala.quoted.*

  object AutoPickleWriter extends AutoDerivation[TapirPickle.Writer]:

    override def split[T](sealedTrait: SealedTrait[sttp.tapir.json.upickle.auto.TapirPickle.Writer, T]): TapirPickle.Writer[T] = ???

    def join[T](ctx: CaseClass[TapirPickle.Writer, T]): TapirPickle.Writer[T] =
      ctx.params(0).typeclass
      null


  val autoIntCodec = implicitly[TapirPickle.ReadWriter[Int]]

  def testMe =
    val dogJsonStr = TapirPickle.write(Dog("Bob", DogAge(3)))
    val intJsonStr = TapirPickle.write(3)

    println(s"Hello, $dogJsonStr, $intJsonStr")
}
