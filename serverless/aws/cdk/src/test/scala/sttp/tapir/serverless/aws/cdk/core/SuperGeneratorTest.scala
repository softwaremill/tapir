package sttp.tapir.serverless.aws.cdk.core

import cats.data.NonEmptyList
import org.scalatest.funsuite.AnyFunSuite
import sttp.tapir.serverless.aws.cdk.core.Method._

class SuperGeneratorTest extends AnyFunSuite {
  test("single resource with single method") {
    // given
    val resource = new Resource(VariableName("variableName"), "root/hi", NonEmptyList.one(GET), "")

    // when
    val result = SuperGenerator.generate(List(resource)).mkString("\n")

    // then
    val expected =
      """
        |const variableNameRoot = api.root.addResource('root');
        |const variableNameHi = variableNameRoot.addResource('hi');
        |variableNameHi.addMethod('GET');
        |""".stripMargin

    assert(result == format(expected))
  }

  test("single resource with two methods") {
    // given
    val resource = new Resource(VariableName("variableName"), "root/hi", NonEmptyList.of(GET, POST), "")

    // when
    val result = SuperGenerator.generate(List(resource)).mkString("\n")

    // then
    val expected =
      """
        |const variableNameRoot = api.root.addResource('root');
        |const variableNameHi = variableNameRoot.addResource('hi');
        |variableNameHi.addMethod('GET');
        |variableNameHi.addMethod('POST');
        |""".stripMargin

    assert(result == format(expected))
  }

  test("single resource with two methods with different order") {
    // given
    val resource = new Resource(VariableName("variableName"), "root/hi", NonEmptyList.of(POST, GET), "")

    // when
    val result = SuperGenerator.generate(List(resource)).mkString("\n")

    // then
    val expected =
      """
        |const variableNameRoot = api.root.addResource('root');
        |const variableNameHi = variableNameRoot.addResource('hi');
        |variableNameHi.addMethod('GET');
        |variableNameHi.addMethod('POST');
        |""".stripMargin

    assert(result == format(expected))
  }

  test("multiple resources with single method") {
    // given
    val resourceA = new Resource(VariableName("hi"), "hi", NonEmptyList.one(GET), "")
    val resourceB = new Resource(VariableName("hiName"), "{name}", NonEmptyList.one(GET), "hi")

    // when
    val value1 = SuperGenerator.generate(List(resourceA, resourceB))
    val result = value1.map(i => if (i != "\n") s"$i" else "").mkString("\n") // fixme

    // then
    val expected =
      """
        |const hi = api.root.addResource('hi');
        |hi.addMethod('GET');
        |
        |const hiName = hi.addResource('{name}');
        |hiName.addMethod('GET');
        |""".stripMargin

    assert(result == format(expected))
  }

  test("multiple resources with single method") {
    // given
    val resourceA = new Resource(VariableName("hi"), "hi/{one}", NonEmptyList.one(GET), "")
    val resourceB = new Resource(VariableName("two"), "two/{two}", NonEmptyList.one(GET), "hi")

    // when
    val value1 = SuperGenerator.generate(List(resourceA, resourceB))
    val result = value1.map(i => if (i != "\n") s"$i" else "").mkString("\n") // fixme

    // then
    val expected =
      """
        |const hi = api.root.addResource('hi');
        |hi.addMethod('GET');
        |
        |const hiName = hi.addResource('{name}');
        |hiName.addMethod('GET');
        |""".stripMargin

    assert(result == format(expected))
  }

  private def format(input: String): String = input.split("\n").drop(1).mkString("\n")
}
