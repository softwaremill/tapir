package sttp.tapir.serverless.aws.cdk.core

import cats.data.NonEmptyList
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SuperGeneratorTest extends AnyFunSuite with Matchers {
  test("single resource with single method") {
    val resource = new Resource("variableName", "root/hi", NonEmptyList.one(GET), "")
    val generator = SuperGenerator
    val result = generator.generate(List(resource)).mkString("\n")
    val expected =
      """
        |const variableName = api.root.addResource('root/hi');
        |variableName.addMethod('GET');
        |""".stripMargin

    assert(result == prepare(expected))
  }

  test("single resource with two methods") {
    val resource = new Resource("variableName", "root/hi", NonEmptyList.of(GET, POST), "")
    val generator = SuperGenerator
    val value1 = generator.generate(List(resource))
    val result = value1.mkString("\n")
    val expected =
      """
        |const variableName = api.root.addResource('root/hi');
        |variableName.addMethod('GET');
        |variableName.addMethod('POST');
        |""".stripMargin

    assert(result == prepare(expected))
  }

  test("single resource with two methods with different order") {
    val resource = new Resource("variableName", "root/hi", NonEmptyList.of(POST, GET), "")
    val generator = SuperGenerator
    val result = generator.generate(List(resource)).mkString("\n")
    val expected =
      """
        |const variableName = api.root.addResource('root/hi');
        |variableName.addMethod('GET');
        |variableName.addMethod('POST');
        |""".stripMargin

    assert(result == prepare(expected))
  }

  test("multiple resources with single method") {
    val resourceA = new Resource("hi", "hi", NonEmptyList.one(GET), "")
    val resourceB = new Resource("hiName", "{name}", NonEmptyList.one(GET), "hi")
    val generator = SuperGenerator
    val value1 = generator.generate(List(resourceA, resourceB))
    val result = value1.map(i => if (i != "\n") s"$i" else "").mkString("\n") //fixme
    val expected =
      """
        |const hi = api.root.addResource('hi');
        |hi.addMethod('GET');
        |
        |const hiName = hi.addResource('{name}');
        |hiName.addMethod('GET');
        |""".stripMargin

    val str = prepare(expected)
    assert(result == str)
  }

  private def prepare(input: String) = input.split("\n").drop(1).mkString("\n")
}
