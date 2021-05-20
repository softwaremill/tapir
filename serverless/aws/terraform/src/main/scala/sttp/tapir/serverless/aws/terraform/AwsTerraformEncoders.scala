package sttp.tapir.serverless.aws.terraform

import io.circe.{Encoder, Json}

object AwsTerraformEncoders {

  private val lambdaFunctionResourceName = "lambda"

  implicit def encoderAwsTerraformOptions(implicit options: AwsTerraformOptions): Encoder[AwsTerraformOptions] = {

    val provider = Json.fromFields(Seq("aws" -> Json.fromValues(Seq(Json.fromFields(Seq("region" -> Json.fromString(options.awsRegion)))))))
    val terraform = Json.fromFields(
      Seq("required_providers" -> Json.fromFields(Seq("aws" -> Json.fromFields(Seq("source" -> Json.fromString("hashicorp/aws"))))))
    )

    val functionSource: Seq[(String, Json)] = options.lambdaSource match {
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
        "function_name" -> Json.fromString(options.lambdaFunctionName),
        "role" -> Json.fromString("${aws_iam_role.lambda_exec.arn}")
      ) ++ functionSource
    )

    val iAmRole = Json.fromFields(
      Seq(
        "name" -> Json.fromString("lambda_exec_role"),
        "assume_role_policy" -> Json.fromString(options.assumeRolePolicy)
      )
    )

    val apiGatewayPermission = Json.fromFields(
      Seq(
        "statement_id" -> Json.fromString("AllowAPIGatewayInvoke"),
        "action" -> Json.fromString("lambda:InvokeFunction"),
        "function_name" -> Json.fromString(s"$${aws_lambda_function.$lambdaFunctionResourceName.function_name}"),
        "principal" -> Json.fromString("apigateway.amazonaws.com"),
        "source_arn" -> Json.fromString(s"$${aws_api_gateway_rest_api.${options.apiGatewayName}.execution_arn}/*/*")
      )
    )

    _ =>
      Json.fromFields(
        Seq(
          "terraform" -> terraform,
          "provider" -> provider,
          "resource" -> Json.fromValues(
            Seq(
              resource("aws_lambda_function", lambdaFunctionResourceName, lambdaFunction),
              resource("aws_iam_role", "lambda_exec", iAmRole),
              resource("aws_lambda_permission", "api_gateway_permission", apiGatewayPermission)
            )
          )
        )
      )
  }

  implicit def encoderAwsTerraformApiGatewayMethods(implicit options: AwsTerraformOptions): Encoder[List[AwsTerraformApiGatewayMethod]] = {
    val rest_api_id = s"$${aws_api_gateway_rest_api.${options.apiGatewayName}.id}"
    val root_resource_id = s"$${aws_api_gateway_rest_api.${options.apiGatewayName}.root_resource_id}"

    val apiGatewayRestApi = Json.fromFields(
      Seq(
        "name" -> Json.fromString("ServerlessFunction"),
        "description" -> Json.fromString("Terraform Serverless Application")
      )
    )

    val apiGatewayResource = Json.fromFields(
      Seq(
        "rest_api_id" -> Json.fromString(rest_api_id),
        "parent_id" -> Json.fromString(root_resource_id),
        "path_part" -> Json.fromString("{proxy+}")
      )
    )

    val apiGatewayMethodProxy = Json.fromFields(
      Seq(
        "rest_api_id" -> Json.fromString(rest_api_id),
        "resource_id" -> Json.fromString("${aws_api_gateway_resource.proxy.id}"),
        "http_method" -> Json.fromString("ANY"),
        "authorization" -> Json.fromString("NONE")
      )
    )

    val apiGatewayIntegration = Json.fromFields(
      Seq(
        "rest_api_id" -> Json.fromString(rest_api_id),
        "resource_id" -> Json.fromString("${aws_api_gateway_method.proxy.resource_id}"),
        "http_method" -> Json.fromString("${aws_api_gateway_method.proxy.http_method}"),
        "integration_http_method" -> Json.fromString("POST"),
        "type" -> Json.fromString("AWS_PROXY"),
        "uri" -> Json.fromString(s"$${aws_lambda_function.$lambdaFunctionResourceName.invoke_arn}")
      )
    )

    val apiGatewayMethodProxyRoot = Json.fromFields(
      Seq(
        "rest_api_id" -> Json.fromString(rest_api_id),
        "resource_id" -> Json.fromString(root_resource_id),
        "http_method" -> Json.fromString("ANY"),
        "authorization" -> Json.fromString("NONE")
      )
    )

    val apiGatewayIntegrationRoot = Json.fromFields(
      Seq(
        "rest_api_id" -> Json.fromString(rest_api_id),
        "resource_id" -> Json.fromString("${aws_api_gateway_method.proxy_root.resource_id}"),
        "http_method" -> Json.fromString("${aws_api_gateway_method.proxy_root.http_method}"),
        "integration_http_method" -> Json.fromString("POST"),
        "type" -> Json.fromString("AWS_PROXY"),
        "uri" -> Json.fromString(s"$${aws_lambda_function.$lambdaFunctionResourceName.invoke_arn}")
      )
    )

    val apiGatewayDeployment = Json.fromFields(
      Seq(
        "depends_on" -> Json.fromValues(
          Seq(
            Json.fromString("aws_api_gateway_integration.lambda"),
            Json.fromString("aws_api_gateway_integration.lambda_root")
          )
        ),
        "rest_api_id" -> Json.fromString(rest_api_id),
        "stage_name" -> Json.fromString("test")
      )
    )

    val output = Json.fromFields(
      Seq(
        "base_url" -> Json.fromFields(
          Seq("value" -> Json.fromString(s"$${aws_api_gateway_deployment.${options.apiGatewayName}.invoke_url}"))
        )
      )
    )

    _ =>
      Json.fromFields(
        Seq(
          "resource" -> Json.fromValues(
            Seq(
              resource("aws_api_gateway_rest_api", s"${options.apiGatewayName}", apiGatewayRestApi),
              // resources below are responsible for forwarding any incoming request to lambda function
              resource("aws_api_gateway_resource", "proxy", apiGatewayResource),
              resource("aws_api_gateway_method", "proxy", apiGatewayMethodProxy),
              resource("aws_api_gateway_integration", "lambda", apiGatewayIntegration),
              resource("aws_api_gateway_method", "proxy_root", apiGatewayMethodProxyRoot),
              resource("aws_api_gateway_integration", "lambda_root", apiGatewayIntegrationRoot),
              //
              resource("aws_api_gateway_deployment", s"${options.apiGatewayName}", apiGatewayDeployment)
            )
          ),
          "output" -> output
        )
      )
  }

  private def resource[R](`type`: String, name: String, encoded: Json): Json =
    Json.fromFields(Seq(`type` -> Json.fromFields(Seq(name -> encoded))))
}
