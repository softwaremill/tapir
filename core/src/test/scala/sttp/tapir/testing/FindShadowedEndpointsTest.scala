package sttp.tapir.testing

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.tapir._

class FindShadowedEndpointsTest extends AnyFlatSpecLike with Matchers {

  it should "should not detect shadowed endpoint when first endpoint has empty path and second fixed path" in {
    val e1 = endpoint.get
    val e2 = endpoint.get.in("x")

    val result = FindShadowedEndpoints(List(e1, e2))

    result shouldBe Set()
  }

  it should "not detect shadowed endpoint when second endpoint doesn't have specified method" in {
    val e1 = endpoint.get.in("x")
    val e2 = endpoint.in("x")

    val result = FindShadowedEndpoints(List(e1, e2))

    result shouldBe Set()
  }

  it should "detect shadowed endpoint when first endpoint doesn't have specified method" in {
    val e1 = endpoint.in("x")
    val e2 = endpoint.get.in("x")

    val result = FindShadowedEndpoints(List(e1, e2))

    val expectedResult = Set(ShadowedEndpoint(e2, e1))
    result shouldBe expectedResult
  }

  it should "should detect shadowed endpoint when first endpoint has only wildcard path and second empty path" in {
    val e1 = endpoint.get.in(paths)
    val e2 = endpoint.get
    val result = FindShadowedEndpoints(List(e1, e2))

    val expectedResult = Set(ShadowedEndpoint(e2, e1))
    result shouldBe expectedResult
  }

  it should "detect shadowed endpoint when http methods are provided in different order" in {
    val e1 = endpoint.in("x").get
    val e2 = endpoint.get.in("x")

    val result = FindShadowedEndpoints(List(e1, e2))

    val expectedResult = Set(ShadowedEndpoint(e2, e1))
    result shouldBe expectedResult
  }

  it should "detect shadowed endpoint when security inputs and inputs overlap" in {
    val e1 = endpoint.get.in("x")
    val e2 = endpoint.get.securityIn("x")

    val result = FindShadowedEndpoints(List(e1, e2))

    val expectedResult = Set(ShadowedEndpoint(e2, e1))
    result shouldBe expectedResult
  }

  it should "should not detect shadowed endpoint when methods are not provided and paths are different" in {
    val e1 = endpoint.in("y")
    val e2 = endpoint.in("x")

    val result = FindShadowedEndpoints(List(e1, e2))

    result shouldBe Set()
  }

  it should "should detect shadowed endpoint when methods are not provided" in {
    val e1 = endpoint.in("x")
    val e2 = endpoint.in("x")

    val result = FindShadowedEndpoints(List(e1, e2))

    val expectedResult = Set(ShadowedEndpoint(e2, e1))
    result shouldBe expectedResult
  }

  it should "omit all segments which are not relevant for shadow check" in {
    val e1 = endpoint.get.in(query[String]("key").and(header[String]("X-Account"))).in("x")
    val e2 = endpoint.get.in("x")

    val result = FindShadowedEndpoints(List(e1, e2))

    val expectedResult = Set(ShadowedEndpoint(e2, e1))
    result shouldBe expectedResult
  }

  it should "should detect shadowed endpoints with path variables that contain special characters" in {
    val e1 = endpoint.get.in("[x]")
    val e2 = endpoint.get.in("x")
    val e3 = endpoint.get.in("/..." / paths)
    val e4 = endpoint.get.in("x/")
    val e5 = endpoint.get.in("x")

    val result = FindShadowedEndpoints(List(e1, e2, e3, e4, e5))

    val expectedResult = Set(ShadowedEndpoint(e5, e2))
    result shouldBe expectedResult
  }

  it should "not detect shadowed endpoint when it is only partially shadowed" in {
    val e1 = endpoint.get.in("x/y/z")
    val e2 = endpoint.get.in("x" / "y" / "z")
    val e3 = endpoint.get.in("x" / paths)

    val result = FindShadowedEndpoints(List(e1, e2, e3))

    result shouldBe Set()
  }

  it should "detect endpoint with path variable shadowed by endpoint with wildcard" in {
    val e1 = endpoint.get.in("x" / paths)
    val e2 = endpoint.get.in("x" / path[String].name("y_2") / "z")
    val e3 = endpoint.get.in("x" / "y" / "x")

    val result = FindShadowedEndpoints(List(e1, e2, e3))

    val expectedResult = Set(ShadowedEndpoint(e2, e1), ShadowedEndpoint(e3, e1))
    result shouldBe expectedResult
  }

  it should "detect shadowed endpoints for endpoints with similar path structure but with different path vars names" in {

    val e1 = endpoint.get.in("x" / "y")
    val e2 = endpoint.get.in("x" / "y" / paths)
    val e3 = endpoint.get.in("x" / path[String].name("y_2") / path[String].name("y_4") / "z1")
    val e4 = endpoint.get.in("x" / path[String].name("y_3") / path[String].name("y_5") / "z1")

    val result = FindShadowedEndpoints(List(e1, e2, e3, e4))

    val expectedResult = Set(ShadowedEndpoint(e4, e3))
    result shouldBe expectedResult
  }

  it should "detect shadowed endpoints for endpoints with path variables and wildcards at the beginning" in {
    val e1 = endpoint.get.in("z" / paths)
    val e2 = endpoint.get.in("z" / "x" / path[String].name("y_2") / path[String].name("y4"))
    val e3 = endpoint.get.in("z" / "x" / path[String].name("y_3") / path[String].name("y5"))
    val e4 = endpoint.get.in("c" / "x" / path[String].name("y_3") / path[String].name("y5"))

    val result = FindShadowedEndpoints(List(e1, e2, e3, e4))

    val expectedResult = Set(ShadowedEndpoint(e2, e1), ShadowedEndpoint(e3, e1))
    result shouldBe expectedResult
  }

  it should "detect shadowed endpoints for endpoints with path variables in the middle" in {
    val e1 = endpoint.get.in("x" / path[String].name("y_1") / "z")
    val e2 = endpoint.get.in("x" / path[String].name("y_2") / path[String].name("y4"))
    val e3 = endpoint.get.in("x" / path[String].name("y_3") / path[String].name("y5"))

    val result = FindShadowedEndpoints(List(e1, e2, e3))

    val expectedResult = Set(ShadowedEndpoint(e3, e2))
    result shouldBe expectedResult
  }

  it should "detect shadowed endpoints for fixed paths" in {
    val e1 = endpoint.get.in("y").out(stringBody)
    val e2 = endpoint.get.in("x").out(stringBody)
    val e3 = endpoint.get.in("x").out(stringBody)
    val e4 = endpoint.get.in("x").out(stringBody)
    val e5 = endpoint.get.in("x" / "y").out(stringBody)
    val e6 = endpoint.post.in("x").out(stringBody)

    val result = FindShadowedEndpoints(List(e1, e2, e3, e4, e5, e6))

    val expectedResult = Set(ShadowedEndpoint(e3, e2), ShadowedEndpoint(e4, e2))
    result shouldBe expectedResult
  }

  it should "return empty result when no shadowed endpoints exist" in {
    val e1 = endpoint.get.in("a").out(stringBody)
    val e2 = endpoint.get.in("b").out(stringBody)
    val e3 = endpoint.get.in("a" / "b" / paths).out(stringBody)
    val e4 = endpoint.get.in("a/b/c").out(stringBody)

    val result = FindShadowedEndpoints(List(e1, e2, e3, e4))
    result shouldBe Set()
  }

  it should "return empty result for empty input" in {
    val result = FindShadowedEndpoints(Nil)
    result shouldBe Set()
  }
}
