package sttp.tapir.serverless.aws.terraform

import io.circe.Json
import sttp.tapir.serverless.aws.terraform.TerraformResource.{TapirApiGateway, terraformResource}

import scala.concurrent.duration.FiniteDuration

sealed trait TerraformResource {
  def json(): Json
}

private[terraform] object TerraformResource {
  val TapirApiGateway = "TapirApiGateway" // main resource name

  def terraformResource[R](`type`: String, name: String, encoded: Json): Json =
    Json.fromFields(Seq(`type` -> Json.fromFields(Seq(name -> encoded))))
}

case class AwsLambdaFunction(name: String, timeout: FiniteDuration, memorySize: Int, source: FunctionSource) extends TerraformResource {
  override def json(): Json = {
    val functionSource: Seq[(String, Json)] = source match {
      case s3: S3Source =>
        Seq(
          "s3_bucket" -> Json.fromString(s3.bucket),
          "s3_key" -> Json.fromString(s3.key),
          "runtime" -> Json.fromString(s3.runtime),
          "handler" -> Json.fromString(s3.handler)
        )
      case image: ImageSource => Seq("image_uri" -> Json.fromString(image.imageUri))
      case code: CodeSource   =>
        Seq(
          "filename" -> Json.fromString(code.fileName),
          "runtime" -> Json.fromString(code.runtime),
          "handler" -> Json.fromString(code.handler)
        )
    }

    val lambdaFunction = Json.fromFields(
      Seq(
        "function_name" -> Json.fromString(name),
        "role" -> Json.fromString("${aws_iam_role.lambda_exec.arn}"),
        "timeout" -> Json.fromLong(timeout.toSeconds),
        "memory_size" -> Json.fromInt(memorySize)
      ) ++ functionSource
    )

    terraformResource("aws_lambda_function", "lambda", lambdaFunction)
  }
}

case class AwsIamRole(assumeRolePolicy: String) extends TerraformResource {
  override def json(): Json = terraformResource(
    "aws_iam_role",
    "lambda_exec",
    Json.fromFields(
      Seq(
        "name" -> Json.fromString("lambda_exec_role"),
        "assume_role_policy" -> Json.fromString(assumeRolePolicy)
      )
    )
  )
}

case object AwsLambdaPermission extends TerraformResource {
  override def json(): Json =
    terraformResource(
      "aws_lambda_permission",
      "api_gateway_permission",
      Json.fromFields(
        Seq(
          "statement_id" -> Json.fromString("AllowAPIGatewayInvoke"),
          "action" -> Json.fromString("lambda:InvokeFunction"),
          "function_name" -> Json.fromString(s"$${aws_lambda_function.lambda.function_name}"),
          "principal" -> Json.fromString("apigateway.amazonaws.com"),
          "source_arn" -> Json.fromString(s"$${aws_apigatewayv2_api.$TapirApiGateway.execution_arn}/*/*")
        )
      )
    )
}

case class AwsApiGatewayV2Api(name: String, description: String) extends TerraformResource {
  override def json(): Json = {
    terraformResource(
      "aws_apigatewayv2_api",
      TapirApiGateway,
      Json.fromFields(
        Seq(
          "name" -> Json.fromString(name),
          "description" -> Json.fromString(description),
          "protocol_type" -> Json.fromString("HTTP")
        )
      )
    )
  }
}

case class AwsApiGatewayV2Route(
    name: String,
    routeKey: String, // "METHOD PATH"
    integration: String
) extends TerraformResource {
  override def json(): Json = terraformResource(
    "aws_apigatewayv2_route",
    name,
    Json.fromFields(
      Seq(
        "api_id" -> Json.fromString(s"$${aws_apigatewayv2_api.$TapirApiGateway.id}"),
        "route_key" -> Json.fromString(routeKey),
        "authorization_type" -> Json.fromString("NONE"),
        "target" -> Json.fromString(s"integrations/$${aws_apigatewayv2_integration.$integration.id}")
      )
    )
  )
}

case class AwsApiGatewayV2Integration(name: String) extends TerraformResource {
  override def json(): Json = terraformResource(
    "aws_apigatewayv2_integration",
    name,
    Json.fromFields(
      Seq(
        "api_id" -> Json.fromString(s"$${aws_apigatewayv2_api.$TapirApiGateway.id}"),
        "integration_type" -> Json.fromString("AWS_PROXY"),
        "integration_method" -> Json.fromString("POST"),
        "integration_uri" -> Json.fromString(s"$${aws_lambda_function.lambda.invoke_arn}"),
        "payload_format_version" -> Json.fromString("2.0")
      )
    )
  )
}

case class AwsApiGatewayV2Deployment(dependsOn: Seq[String]) extends TerraformResource {
  override def json(): Json =
    terraformResource(
      "aws_apigatewayv2_deployment",
      TapirApiGateway,
      Json.fromFields(
        Seq(
          "depends_on" -> Json.fromValues(
            dependsOn.map { d => Json.fromString(s"aws_apigatewayv2_route.$d") }
          ),
          "api_id" -> Json.fromString(s"$${aws_apigatewayv2_api.$TapirApiGateway.id}")
        )
      )
    )
}

case class AwsApiGatewayV2Stage(stage: String, autoDeploy: Boolean) extends TerraformResource {
  override def json(): Json =
    terraformResource(
      "aws_apigatewayv2_stage",
      TapirApiGateway,
      Json.fromFields(
        Seq(
          "api_id" -> Json.fromString(s"$${aws_apigatewayv2_api.$TapirApiGateway.id}"),
          "name" -> Json.fromString(stage),
          "auto_deploy" -> Json.fromBoolean(autoDeploy)
        )
      )
    )
}
