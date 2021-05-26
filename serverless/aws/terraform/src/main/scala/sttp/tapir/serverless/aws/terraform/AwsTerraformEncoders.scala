package sttp.tapir.serverless.aws.terraform

import io.circe.{Encoder, Json}
import sttp.tapir.serverless.aws.terraform.TerraformResource.TapirApiGateway

object AwsTerraformEncoders {

  implicit def encoderAwsTerraformApiGateway(implicit options: AwsTerraformOptions): Encoder[AwsTerraformApiGateway] =
    gateway => {

      val provider =
        Json.fromFields(Seq("aws" -> Json.fromValues(Seq(Json.fromFields(Seq("region" -> Json.fromString(options.awsRegion)))))))
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
              AwsLambdaFunction(options.functionName, options.timeout, options.memorySize, options.functionSource).json(),
              AwsIamRole(options.assumeRolePolicy.noSpaces).json(),
              AwsLambdaPermission.json(),
              AwsApiGatewayV2Api(options.apiGatewayName, options.apiGatewayDescription).json(),
              AwsApiGatewayV2Deployment(integrations.collect { case i @ AwsApiGatewayV2Integration(_) => i.name }).json(),
              AwsApiGatewayV2Stage(options.apiGatewayStage, options.autoDeploy).json()
            ) ++ integrations.map(_.json())
          ),
          "output" -> output
        )
      )
    }
}
