package sttp.tapir.serverless.aws.sam.parameter

import cats.data.NonEmptyList
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.serverless.aws.sam.{AwsSamOptions, CodeSource, ImageSource}

class AwsSamOptionsParameterizedTest extends AnyFlatSpec with Matchers {

  private val sampleOptions = AwsSamOptions(
    namePrefix = "namePrefix",
    source = ImageSource(imageUri = "imageUri")
  )

  "AwsSamOptionsParameterized.withParameter" should "compile inputs" in {
    AwsSamOptionsParameterized
      .empty(sampleOptions)
      .withParameter("Param1"): AwsSamOptionsParameterized[InputParameter]

    AwsSamOptionsParameterized
      .empty(sampleOptions)
      .withParameter("Param1")
      .withParameter("Param2"): AwsSamOptionsParameterized[(InputParameter, InputParameter)]

    AwsSamOptionsParameterized
      .empty(sampleOptions)
      .withParameter("Param1")
      .withParameter("Param2")
      .withParameter("Param3"): AwsSamOptionsParameterized[(InputParameter, InputParameter, InputParameter)]

  }

  "AwsSamOptionsParameterized.customiseOptions" should "update originalOptions and return updated options" in {

    def customiseOptions(params: (InputParameter, InputParameter, InputParameter), originalOptions: AwsSamOptions): AwsSamOptions = {
      val (param1, param2, param3) = params
      originalOptions.copy(
        source = CodeSource(runtime = param1.ref, codeUri = param2.ref, handler = param3.ref)
      )
    }

    val actualResult = sampleOptions
      .withParameter("Param1")
      .withParameter("Param2", Some("Role ARN"))
      .withParameter("Param3", Some("DynamoDb URL"))
      .customiseOptions(customiseOptions)

    val expectedResult =
      AwsSamOptions(
        namePrefix = "namePrefix",
        source = CodeSource(runtime = "!Ref Param1", codeUri = "!Ref Param2", handler = "!Ref Param3"),
        parameters = Some(
          NonEmptyList.of(
            InputParameter("Param1", None),
            InputParameter("Param2", Some("Role ARN")),
            InputParameter("Param3", Some("DynamoDb URL"))
          )
        )
      )

    actualResult shouldBe expectedResult
  }

}
