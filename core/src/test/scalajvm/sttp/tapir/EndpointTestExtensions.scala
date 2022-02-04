package sttp.tapir

trait EndpointTestExtensions { self: EndpointTest =>
  "endpoint" should "not compile invalid outputs with queries" in {
    assertTypeError("""import sttp.tapir._
                      |endpoint.out(query[String]("q1"))""".stripMargin)
  }

  it should "not compile invalid outputs with paths" in {
    assertTypeError("""import sttp.tapir._
                      |endpoint.out(path[String])""".stripMargin)
  }

  "oneOfVariant.appliesTo" should "check if a value applies to the variant using a runtime class check" in {
    // non-primitive
    oneOfVariant(emptyOutputAs("")).appliesTo("x") shouldBe true
    oneOfVariant(emptyOutputAs("")).appliesTo(10) shouldBe false

    // primitives
    oneOfVariant(emptyOutputAs(0: Byte)).appliesTo(1: Byte) shouldBe true
    oneOfVariant(emptyOutputAs(0: Byte)).appliesTo("") shouldBe false
    oneOfVariant(emptyOutputAs(0: Byte)).appliesTo(0) shouldBe false

    oneOfVariant(emptyOutputAs(0: Short)).appliesTo(1: Short) shouldBe true
    oneOfVariant(emptyOutputAs(0: Short)).appliesTo("") shouldBe false
    oneOfVariant(emptyOutputAs(0: Short)).appliesTo(0) shouldBe false

    oneOfVariant(emptyOutputAs('x')).appliesTo('y') shouldBe true
    oneOfVariant(emptyOutputAs('x')).appliesTo("") shouldBe false
    oneOfVariant(emptyOutputAs('x')).appliesTo(0) shouldBe false

    oneOfVariant(emptyOutputAs(0)).appliesTo(10) shouldBe true
    oneOfVariant(emptyOutputAs(0)).appliesTo("") shouldBe false
    oneOfVariant(emptyOutputAs(0)).appliesTo(false) shouldBe false

    oneOfVariant(emptyOutputAs(0L)).appliesTo(1L) shouldBe true
    oneOfVariant(emptyOutputAs(0L)).appliesTo("") shouldBe false
    oneOfVariant(emptyOutputAs(0L)).appliesTo(1) shouldBe false

    oneOfVariant(emptyOutputAs(0.0f)).appliesTo(1.0f) shouldBe true
    oneOfVariant(emptyOutputAs(0.0f)).appliesTo("") shouldBe false
    oneOfVariant(emptyOutputAs(0.0f)).appliesTo(1.0) shouldBe false

    oneOfVariant(emptyOutputAs(0.0)).appliesTo(1.0) shouldBe true
    oneOfVariant(emptyOutputAs(0.0)).appliesTo("") shouldBe false
    oneOfVariant(emptyOutputAs(0.0)).appliesTo(1.0f) shouldBe false

    oneOfVariant(emptyOutputAs(true)).appliesTo(false) shouldBe true
    oneOfVariant(emptyOutputAs(true)).appliesTo("") shouldBe false
    oneOfVariant(emptyOutputAs(true)).appliesTo(0) shouldBe false

    oneOfVariant(emptyOutputAs(())).appliesTo(()) shouldBe true
    oneOfVariant(emptyOutputAs(())).appliesTo("") shouldBe false
    oneOfVariant(emptyOutputAs(())).appliesTo(false) shouldBe false
  }
}
