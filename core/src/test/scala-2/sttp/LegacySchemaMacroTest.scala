package sttp

import sttp.tapir.SchemaType._
import sttp.tapir.generic.{Configuration, D}
import org.scalatest.flatspec.AnyFlatSpec
import sttp.tapir.generic.auto._
import org.scalatest.matchers.should.Matchers
import sttp.tapir.TestUtil.field
import sttp.tapir._

class LegacySchemaMacroTest extends AnyFlatSpec with Matchers {
  it should "work with custom naming configuration" in {
    implicit val customConf: Configuration = Configuration.default.withKebabCaseMemberNames
    val actual = implicitly[Schema[D]].modify(_.someFieldName)(_.description("something"))
    actual.schemaType shouldBe SProduct[D](
      SObjectInfo("sttp.tapir.generic.D"),
      List(field(FieldName("someFieldName", "some-field-name"), Schema(SString()).description("something")))
    )
  }  
}
