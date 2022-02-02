package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Schema.SName
import sttp.tapir.SchemaMacroTestData2.ValueClasses.DoubleValue
import sttp.tapir.SchemaMacroTestData2.{MyString, Type, ValueClasses}
import sttp.tapir.SchemaType.{SArray, SProduct, SString}
import sttp.tapir.TestUtil.field
import sttp.tapir.generic.auto._
import sttp.tapir.internal.SchemaAnnotations

// tests which pass only on Scala2
class SchemaMacroTest2 extends AnyFlatSpec with Matchers {

  // value classes are not supported by Magnolia for Scala3: https://github.com/softwaremill/magnolia/issues/296
  it should "derive schema for a simple value classes" in {
    implicitly[Schema[ValueClasses.UserName]] shouldBe Schema.string[ValueClasses.UserName]
  }

  it should "derive schema for numeric value class with format" in {
    val schema = Schema.derived[DoubleValue]

    schema.show shouldBe Schema.schemaForDouble.show
  }

  it should "derive schema for a class containing a value class" in {
    val expected = Schema(
      SProduct[ValueClasses.UserNameRequest](
        List(field(FieldName("name"), Schema(SString())))
      ),
      Some(SName("sttp.tapir.SchemaMacroTestData2.ValueClasses.UserNameRequest"))
    )

    implicitly[Schema[ValueClasses.UserNameRequest]] shouldBe expected
  }

  it should "derive schema for a class containing a value class with a list of value classes" in {
    val expected2 = Schema(
      SProduct[ValueClasses.UserListRequest](
        List(field(FieldName("list"), Schema(SArray[ValueClasses.UserList, String](Schema.schemaForString)(_.list.map(_.name)))))
      ),
      Some(SName("sttp.tapir.SchemaMacroTestData2.ValueClasses.UserListRequest"))
    )

    implicitly[Schema[ValueClasses.UserListRequest]] shouldBe expected2
  }

  it should "fail to derive schema for nested generic value class with meaningful msg" in {
    val ex = the[IllegalArgumentException] thrownBy schemaForCaseClass[Type.MapType]
    ex.getMessage.contains("requirement failed: Cannot derive schema for generic value class") shouldBe true
  }

  it should "derive schema annotations and enrich schema" in {
    val baseSchema = Schema.string[MyString]

    val enriched = implicitly[SchemaAnnotations[MyString]].enrich(baseSchema)

    enriched shouldBe Schema
      .string[MyString]
      .description("my-string")
      .encodedExample("encoded-example")
      .default(MyString("default"))
      .format("utf8")
      .deprecated(true)
      .name(SName("encoded-name"))
      .validate(Validator.pass[MyString])
  }
}
