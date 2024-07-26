package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class EndpointScala3Test extends AnyFlatSpec with Matchers:
  import EndpointScala3Test.*

  "oneOfVariant" should "compile when using parametrised enums" in {
    endpoint.get
      .out(
        sttp.tapir.oneOf(
          oneOfVariant(header[String]("X").mapTo[ParametrisedEnum.Case1]),
          oneOfVariant(header[Int]("Y").mapTo[ParametrisedEnum.Case2])
        )
      )
  }

  it should "compile when using parameterless sealed traits" in {
    endpoint.get
      .out(
        sttp.tapir.oneOf(
          oneOfVariant(emptyOutputAs(ParameterlessSealed.Case1)),
          oneOfVariant(emptyOutputAs(ParameterlessSealed.Case1))
        )
      )
  }

  // parameterless enums aren't translated to run-time classes, so the run-time checks will "glue" any parameterless
  // instance to the first variant corresponding to a parameterless case
  it should "not compile when using parameterless enums" in {
    assertDoesNotCompile("""
      endpoint.get
        .out(
          sttp.tapir.oneOf(
            oneOfVariant(emptyOutputAs(ParameterlessEnum.Case1)),
            oneOfVariant(emptyOutputAs(ParameterlessEnum.Case1))
          )
        )
    """)
  }

object EndpointScala3Test:
  enum ParametrisedEnum:
    case Case1(v: String)
    case Case2(n: Int)

  enum ParameterlessEnum:
    case Case1
    case Case2

  sealed trait ParameterlessSealed
  object ParameterlessSealed:
    case object Case1 extends ParameterlessSealed
    case object Case2 extends ParameterlessSealed
