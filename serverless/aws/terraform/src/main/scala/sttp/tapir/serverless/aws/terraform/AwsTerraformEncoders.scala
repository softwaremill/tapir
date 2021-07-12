package sttp.tapir.serverless.aws.terraform

import io.circe.{Encoder, Json}
import sttp.tapir.serverless.aws.terraform.TerraformResource.TapirApiGateway

trait AwsTerraformEncoders {
  def awsTerraformOptions: AwsTerraformOptions

  implicit def encoderAwsTerraformApiGateway: Encoder[AwsTerraformApiGateway] = gateway => {
    val provider =
      Json.fromFields(
        Seq("aws" -> Json.fromValues(Seq(Json.fromFields(Seq("region" -> Json.fromString(awsTerraformOptions.awsRegion))))))
      )
    val terraform = Json.fromFields(
      Seq("required_providers" -> Json.fromFields(Seq("aws" -> Json.fromFields(Seq("source" -> Json.fromString("hashicorp/aws"))))))
    )

    val integrations: Seq[TerraformResource] = gateway.routes.flatMap { m =>
      val integration = AwsApiGatewayV2Integration(m.name)
      val route = AwsApiGatewayV2Route(m.name, s"${m.httpMethod.method} /${m.path}", m.name)
      Seq(integration, route)
    }

    val output = Json.fromFields(
      Seq(
        "base_url" -> Json.fromFields(
          Seq("value" -> Json.fromString(s"$${aws_apigatewayv2_api.$TapirApiGateway.api_endpoint}"))
        )
      )
    )

    Json.fromFields(
      Seq(
        "terraform" -> terraform,
        "provider" -> provider,
        "resource" -> Json.fromValues(
          Seq(
            AwsLambdaFunction(
              awsTerraformOptions.functionName,
              awsTerraformOptions.timeout,
              awsTerraformOptions.memorySize,
              awsTerraformOptions.functionSource
            ).json(),
            AwsIamRole(awsTerraformOptions.assumeRolePolicy.noSpaces).json(),
            AwsLambdaPermission.json(),
            AwsApiGatewayV2Api(awsTerraformOptions.apiGatewayName, awsTerraformOptions.apiGatewayDescription).json(),
            AwsApiGatewayV2Deployment(integrations.collect { case i @ AwsApiGatewayV2Integration(_) => i.name }).json(),
            AwsApiGatewayV2Stage(awsTerraformOptions.apiGatewayStage, awsTerraformOptions.autoDeploy).json()
          ) ++ integrations.map(_.json())
        ),
        "output" -> output
      )
    )
  }
}

object AwsTerraformEncoders {
  def apply(options: AwsTerraformOptions): AwsTerraformEncoders = new AwsTerraformEncoders {
    override val awsTerraformOptions: AwsTerraformOptions = options
  }
}
