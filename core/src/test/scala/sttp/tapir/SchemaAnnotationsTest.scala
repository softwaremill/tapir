package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Schema.SName
import sttp.tapir.SchemaAnnotationsTestData.{MyCustomisedString, MyString}

class SchemaAnnotationsTest extends AnyFlatSpec with Matchers {
  behavior of "SchemaAnnotations enrich"

  it should "derive schema annotations and enrich schema" in {
    val baseSchema = Schema.string[MyString]

    val enriched = implicitly[SchemaAnnotations[MyString]].enrich(baseSchema)

    enriched shouldBe Schema
      .string[MyString]
      .description("my-string")
      .encodedExample("encoded-example")
      .default(MyString("default"), encoded = Some("encoded-default"))
      .format("utf8")
      .deprecated(true)
      .hidden(true)
      .name(SName("encoded-name"))
      .validate(Validator.pass[MyString])
  }

  it should "apply customise annotations in declaration order, after the metadata annotations" in {
    val enriched = implicitly[SchemaAnnotations[MyCustomisedString]].enrich(Schema.string[MyCustomisedString])

    enriched.format shouldBe Some("c1-c2")
  }

  behavior of "SchemaAnnotations copy"

  it should "keep the customise functions" in {
    val annotations = implicitly[SchemaAnnotations[MyCustomisedString]].copy(description = Some("d"))

    val enriched = annotations.enrich(Schema.string[MyCustomisedString])

    enriched.description shouldBe Some("d")
    enriched.format shouldBe Some("c1-c2")
  }
}
