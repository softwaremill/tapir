package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.tapir.util.ShadowedEndpointChecker

class ShadowedEndpointCheckerTest extends AnyFlatSpecLike with Matchers {

  it should "omit all segments which are not relevant for shadow check" in {
    val e1 = endpoint.get.in(query[String]("key").and(header[String]("X-Account"))).in("x")
    val e2 = endpoint.get.in("x")

    val result = ShadowedEndpointChecker(List(e1, e2))

    val expectedResult = List(ShadowedEndpoint(e2, e1))
    result shouldBe expectedResult
  }

  it should "should detect shadowed endpoints with path variables that contain special characters" in {
    val e1 = endpoint.get.in("[x]")
    val e2 = endpoint.get.in("x")
    val e3 = endpoint.get.in("/..." / paths)
    val e4 = endpoint.get.in("x/")
    val e5 = endpoint.get.in("x")

    val result = ShadowedEndpointChecker(List(e1, e2, e3, e4, e5))

    val expectedResult = List(ShadowedEndpoint(e5, e2))
    result shouldBe expectedResult
  }

  it should "not detect shaded endpoint when it is only partially shadowed" in {
    val e1 = endpoint.get.in("x/y/z")
    val e2 = endpoint.get.in("x" / "y" / "z")
    val e3 = endpoint.get.in("x" / paths)

    val result = ShadowedEndpointChecker(List(e1, e2, e3))

    result shouldBe Nil
  }

  it should "detect endpoint with path variable shadowed by endpoint with wildcard" in {
    val e1 = endpoint.get.in("x" / paths)
    val e2 = endpoint.get.in("x" / path[String].name("y_2") / "z")
    val e3 = endpoint.get.in("x" / "y" / "x")

    val result = ShadowedEndpointChecker(List(e1, e2, e3))

    val expectedResult = List(ShadowedEndpoint(e2, e1), ShadowedEndpoint(e3, e1))
    result shouldBe expectedResult
  }

  it should "detect shadowed endpoints for endpoints with similar path structure but with different path vars names" in {

    val e1 = endpoint.get.in("x" / "y")
    val e2 = endpoint.get.in("x" / "y" / paths)
    val e3 = endpoint.get.in("x" / path[String].name("y_2") / path[String].name("y_4") / "z1")
    val e4 = endpoint.get.in("x" / path[String].name("y_3") / path[String].name("y_5") / "z1")

    val result = ShadowedEndpointChecker(List(e1, e2, e3, e4))

    val expectedResult = List(ShadowedEndpoint(e4, e3))
    result shouldBe expectedResult
  }

  it should "detect shadowed endpoints for endpoints with path variables and wildcards at the beginning" in {
    val e1 = endpoint.get.in("z" / paths)
    val e2 = endpoint.get.in("z" / "x" / path[String].name("y_2") / path[String].name("y4"))
    val e3 = endpoint.get.in("z" / "x" / path[String].name("y_3") / path[String].name("y5"))
    val e4 = endpoint.get.in("c" / "x" / path[String].name("y_3") / path[String].name("y5"))

    val result = ShadowedEndpointChecker(List(e1, e2, e3, e4))

    val expectedResult = List(ShadowedEndpoint(e2, e1), ShadowedEndpoint(e3, e1))
    result shouldBe expectedResult
  }

  it should "detect shadowed endpoints for endpoints with path variables in the middle" in {
    val e1 = endpoint.get.in("x" / path[String].name("y_1") / "z")
    val e2 = endpoint.get.in("x" / path[String].name("y_2") / path[String].name("y4"))
    val e3 = endpoint.get.in("x" / path[String].name("y_3") / path[String].name("y5"))

    val result = ShadowedEndpointChecker(List(e1, e2, e3))

    val expectedResult = List(ShadowedEndpoint(e3, e2))
    result shouldBe expectedResult
  }

  it should "detect shadowed endpoints for fixed paths" in {
    val e1 = endpoint.get.in("y").out(stringBody)
    val e2 = endpoint.get.in("x").out(stringBody)
    val e3 = endpoint.get.in("x").out(stringBody)
    val e4 = endpoint.get.in("x").out(stringBody)
    val e5 = endpoint.get.in("x" / "y").out(stringBody)
    val e6 = endpoint.post.in("x").out(stringBody)

    val result = ShadowedEndpointChecker(List(e1, e2, e3, e4, e5, e6))

    val expectedResult = List(ShadowedEndpoint(e3, e2), ShadowedEndpoint(e4, e2))
    result shouldBe expectedResult
  }

  it should "return empty result when no shadowed endpoints exist" in {
    val e1 = endpoint.get.in("a").out(stringBody)
    val e2 = endpoint.get.in("b").out(stringBody)
    val e3 = endpoint.get.in("a" / "b" / paths).out(stringBody)
    val e4 = endpoint.get.in("a/b/c").out(stringBody)

    val result = ShadowedEndpointChecker(List(e1, e2, e3, e4))
    result shouldBe Nil
  }

  it should "return empty result for empty input" in {
    val result = ShadowedEndpointChecker(Nil)
    result shouldBe Nil
  }
}
