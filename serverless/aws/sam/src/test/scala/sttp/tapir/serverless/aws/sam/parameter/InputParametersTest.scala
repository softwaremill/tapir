package sttp.tapir.serverless.aws.sam.parameter

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.internal.ParamsAsVector
import sttp.tapir.serverless.aws.sam.{AwsSamOptions, ImageSource}

class InputParametersTest extends AnyFlatSpec with Matchers {

  private val sampleOptions = AwsSamOptions(
    namePrefix = "namePrefix",
    source = ImageSource(imageUri = "imageUri")
  )

  "InputParameters.combine" should "combine three inputs and probably it can combine inputs up to 22" in {
    val parameterizedOptions =
      sampleOptions
        .withParameter("param1")
        .withParameter("param2", Some("dynamodb's url"))
        .withParameter("param3", Some("lambda's role"))

    val actualResult = InputParameters.combine(parameterizedOptions.params)
    val expectedResult = {
      val param1 = InputParameter("param1", None)
      val param2 = InputParameter("param2", Some("dynamodb's url"))
      val param3 = InputParameter("param3", Some("lambda's role"))
      ParamsAsVector(Vector(param1, param2, param3))
    }
    actualResult shouldBe expectedResult
  }

  "InputParameters.toList" should "return params in a list with reversed order" in {
    val parameterizedOptions =
      sampleOptions
        .withParameter("param1")
        .withParameter("param2", Some("dynamodb's url"))
        .withParameter("param3", Some("lambda's role"))

    val actualResult = InputParameters.toList(parameterizedOptions.params)
    val expectedResult = {
      val param1 = InputParameter("param1", None)
      val param2 = InputParameter("param2", Some("dynamodb's url"))
      val param3 = InputParameter("param3", Some("lambda's role"))
      List(param1, param2, param3)
    }
    actualResult shouldBe expectedResult
  }
}
