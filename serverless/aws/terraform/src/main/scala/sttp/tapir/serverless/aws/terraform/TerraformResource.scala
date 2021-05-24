package sttp.tapir.serverless.aws.terraform

import io.circe.Json

import scala.concurrent.duration.FiniteDuration

sealed trait TerraformResource {
  def json(): Json
  protected def terraformResource[R](`type`: String, name: String, encoded: Json): Json =
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
      case code: CodeSource =>
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

case class AwsLambdaPermission(apiGatewayName: String) extends TerraformResource {
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
          "source_arn" -> Json.fromString(s"$${aws_api_gateway_rest_api.$apiGatewayName.execution_arn}/*/*")
        )
      )
    )
}

case class AwsApiGatewayRestApi(name: String, description: String) extends TerraformResource {
  override def json(): Json = {
    terraformResource(
      "aws_api_gateway_rest_api",
      s"$name",
      Json.fromFields(
        Seq(
          "name" -> Json.fromString(name),
          "description" -> Json.fromString(description)
        )
      )
    )
  }
}

case class AwsApiGatewayResource(name: String, restApiId: String, parentId: String, pathPart: String) extends TerraformResource {
  override def json(): Json =
    terraformResource(
      "aws_api_gateway_resource",
      name,
      Json.fromFields(
        Seq(
          "rest_api_id" -> Json.fromString(s"$${aws_api_gateway_rest_api.$restApiId.id}"),
          "parent_id" -> Json.fromString(s"$${aws_api_gateway_resource.$parentId.id}"),
          "path_part" -> Json.fromString(pathPart)
        )
      )
    )
}

case class AwsApiGatewayMethod(
    name: String,
    restApiId: String,
    resourceId: String,
    httpMethod: String,
    requestParameters: Seq[(String, Boolean)]
) extends TerraformResource {
  override def json(): Json = terraformResource(
    "aws_api_gateway_method",
    name,
    Json.fromFields(
      Seq(
        "rest_api_id" -> Json.fromString(s"$${aws_api_gateway_rest_api.$restApiId.id}"),
        "resource_id" -> Json.fromString(s"$${aws_api_gateway_resource.$resourceId.id}"),
        "http_method" -> Json.fromString(httpMethod),
        "authorization" -> Json.fromString("NONE")
      ) ++ (if (requestParameters.nonEmpty)
              Seq("request_parameters" -> Json.fromFields(requestParameters.map { case (name, required) =>
                name -> Json.fromBoolean(required)
              }))
            else Seq.empty)
    )
  )
}

case class AwsApiGatewayIntegration(name: String, restApiId: String) extends TerraformResource {
  override def json(): Json = terraformResource(
    "aws_api_gateway_integration",
    name,
    Json.fromFields(
      Seq(
        "rest_api_id" -> Json.fromString(s"$${aws_api_gateway_rest_api.$restApiId.id}"),
        "resource_id" -> Json.fromString(s"$${aws_api_gateway_method.$name.resource_id}"),
        "http_method" -> Json.fromString(s"$${aws_api_gateway_method.$name.http_method}"),
        "integration_http_method" -> Json.fromString("POST"),
        "type" -> Json.fromString("AWS_PROXY"),
        "uri" -> Json.fromString(s"$${aws_lambda_function.lambda.invoke_arn}")
      )
    )
  )
}

case class AwsApiGatewayDeployment(name: String, restApiId: String, integrations: Seq[AwsApiGatewayIntegration]) extends TerraformResource {
  override def json(): Json =
    terraformResource(
      "aws_api_gateway_deployment",
      name,
      Json.fromFields(
        Seq(
          "depends_on" -> Json.fromValues(
            integrations.map { i => Json.fromString(s"aws_api_gateway_integration.${i.name}") }
          ),
          "rest_api_id" -> Json.fromString(s"$${aws_api_gateway_rest_api.$restApiId.id}"),
          "stage_name" -> Json.fromString("test")
        )
      )
    )
}
