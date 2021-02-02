package sttp.tapir.internal

import sttp.tapir._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RichEndpointInputTest extends AnyFlatSpec with Matchers {
  import ins._

  private val testEndpoint = {
    endpoint.put.in("api" / path[String]("version")).in(q1).in(q2).in(q3).in(h1).in(h2).in(authB)
  }

  "pathTo" should "find path to self" in {
    testEndpoint.input.pathTo(testEndpoint.input) shouldBe Vector(testEndpoint.input)
  }

  it should "not find path to non existing input" in {
    testEndpoint.input.pathTo(query[String]("random")) shouldBe Vector.empty
  }

  it should "find path to input down the hierarchy" in {
    testEndpoint.input.pathTo(authB.input) shouldBe Vector(testEndpoint.input, authB, authB.input)
  }

  it should "find path to input from the middle of hierarchy" in {
    authB.pathTo(authB.input) shouldBe Vector(authB, authB.input)
  }

  it should "find path to the middle of hierarchy" in {
    testEndpoint.input.pathTo(authB) shouldBe Vector(testEndpoint.input, authB)
  }

  object ins {
    case class Wrap(value: String)
    val q1 = query[String]("q1")
    val q2 = query[Long]("q2")
    val q3 = query[String]("q3").mapTo(Wrap)

    val h1 = header[String]("h1")
    val h2 = header[String]("h2").mapTo(Wrap)

    val authB = auth.bearer[String]()

  }
}
