package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Schema.SName
import sttp.tapir.Schema.annotations.{validate, validateEach}
import sttp.tapir.generic.auto._

import java.io._

class SchemaSerialisationTest extends AnyFlatSpec with Matchers {

  case class Person(@validate(Validator.minLength(3): Validator[String]) name: String, @validateEach(Validator.min(18)) age: Option[Int])
  case class Family(person1: Person, person2: Person, others: List[Person])

  sealed trait Entity {
    def kind: String
  }
  case class User(firstName: String, lastName: String) extends Entity {
    def kind: String = "user"
  }
  case class Organization(name: String) extends Entity {
    def kind: String = "org"
  }

  private val schemasToSerialise = List(
    Schema.string,
    Schema.string
      .description("x")
      .encodedExample("y")
      .validate(Validator.minLength(1))
      .deprecated(true)
      .format("z")
      .name(SName("a", List("b")))
      .default("y", Some("Y")),
    Schema.schemaForInstant,
    Schema.schemaForUUID,
    Schema.string.asOption,
    Schema.string.asIterable[List],
    implicitly[Schema[Person]],
    implicitly[Schema[Family]],
    Schema
      .oneOfUsingField[Entity, String](_.kind, _.toString)("user" -> implicitly[Schema[User]], "org" -> implicitly[Schema[Organization]])
  )

  for (schema <- schemasToSerialise) {
    it should s"serialise and deserialize $schema" in {
      val output = new ByteArrayOutputStream()
      val objectOutput = new ObjectOutputStream(output)
      objectOutput.writeObject(schema)
      objectOutput.close()

      val input = new ObjectInputStreamWithCustomClassLoader(new ByteArrayInputStream(output.toByteArray))
      val deserialized = input.readObject.asInstanceOf[Schema[Any]]
      deserialized shouldBe schema
    }
  }

  it should "run validation on a deserialized object" in {
    val schema = Schema.derived[Person]
    val output = new ByteArrayOutputStream()
    val objectOutput = new ObjectOutputStream(output)
    objectOutput.writeObject(schema)
    objectOutput.close()

    val input = new ObjectInputStreamWithCustomClassLoader(new ByteArrayInputStream(output.toByteArray))
    val deserialized = input.readObject.asInstanceOf[Schema[Any]]
    deserialized shouldBe schema

    val p = Person("x", Some(10))
    schema.applyValidation(p) shouldBe deserialized.applyValidation(p)
  }

  // needed so that tests pass also when run from sbt
  // see https://stackoverflow.com/questions/60750717/spark-java-lang-classcastexception-cannot-assign-instance-of-scala-collection
  class ObjectInputStreamWithCustomClassLoader(input: InputStream) extends ObjectInputStream(input) {
    override def resolveClass(desc: java.io.ObjectStreamClass): Class[_] = {
      try { Class.forName(desc.getName, false, getClass.getClassLoader) }
      catch { case _: ClassNotFoundException => super.resolveClass(desc) }
    }
  }
}
