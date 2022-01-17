package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Schema.SName
import sttp.tapir.SchemaMacroTestData2.ValueClasses
import sttp.tapir.SchemaType.{SArray, SProduct, SString}
import sttp.tapir.TestUtil.field
import sttp.tapir.generic.auto._

// tests which pass only on Scala2
class SchemaMacroTest2 extends AnyFlatSpec with Matchers {

  // value classes are not supported by Magnolia for Scala3: https://github.com/softwaremill/magnolia/issues/296
  it should "derive schema for a simple value classes" in {
    implicitly[Schema[ValueClasses.UserName]] shouldBe Schema.string[ValueClasses.UserName]
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
}
