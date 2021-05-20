package sttp.tapir.serverless.aws.terraform

import io.circe.{Encoder, Json}

object AwsTerraformEncoders {

  private val lambdaFunctionResourceName = "lambda"

  implicit def encoderAwsLambdaFunction(implicit options: AwsTerraformOptions): Encoder[AwsLambdaFunction] = {
    val encoder: Encoder[AwsLambdaFunction] =
      Encoder.forProduct6("function_name", "role", "handler", "runtime", "s3_bucket", "s3_key")(l =>
        (l.functionName, "${aws_iam_role.lambda_exec.arn}", l.handler, l.runtime, l.s3Bucket, l.s3Key)
      )

    val provider = Json.fromFields(Seq("aws" -> Json.fromValues(Seq(Json.fromFields(Seq("region" -> Json.fromString(options.awsRegion)))))))
    val terraform = Json.fromFields(
      Seq("required_providers" -> Json.fromFields(Seq("aws" -> Json.fromFields(Seq("source" -> Json.fromString("hashicorp/aws"))))))
    )

    val iAmRole = Json.fromFields(
      Seq(
        "name" -> Json.fromString("lambda_exec_role"),
        "assume_role_policy" -> Json.fromString(options.assumeRolePolicy)
      )
    )

    lambda =>
      Json.fromFields(
        Seq(
          "terraform" -> terraform,
          "provider" -> provider,
          "resource" -> Json.fromValues(
            Seq(
              resource("aws_lambda_function", "lambda", encoder(lambda)),
              resource("aws_iam_role", "lambda_exec", iAmRole)
            )
          )
        )
      )
  }

  implicit def encoderTerraformAwsApiGatewayMethods(implicit options: AwsTerraformOptions): Encoder[List[TerraformAwsApiGatewayMethod]] = {
    val rest_api_id = s"aws_api_gateway_rest_api.${options.appName}.id"
    val root_resource_id = s"aws_api_gateway_rest_api.${options.appName}.root_resource_id"

    val apiGatewayRestApi = Json.fromFields(
      Seq(
        "name" -> Json.fromString("ServerlessFunction"),
        "description" -> Json.fromString("Terraform Serverless Application")
      )
    )

    val apiGatewayResource = Json.fromFields(
      Seq(
        "rest_api_id" -> Json.fromString(rest_api_id),
        "resource_id" -> Json.fromString(root_resource_id),
        "path_part" -> Json.fromString("{proxy+}")
      )
    )

    val apiGatewayMethodProxy = Json.fromFields(
      Seq(
        "rest_api_id" -> Json.fromString(rest_api_id),
        "resource_id" -> Json.fromString("aws_api_gateway_resource.proxy.id"),
        "http_method" -> Json.fromString("ANY"),
        "authorization" -> Json.fromString("NONE")
      )
    )

    val apiGatewayIntegration = Json.fromFields(
      Seq(
        "rest_api_id" -> Json.fromString(rest_api_id),
        "resource_id" -> Json.fromString("aws_api_gateway_method.proxy.resource_id"),
        "http_method" -> Json.fromString("aws_api_gateway_method.proxy.http_method"),
        "integration_http_method" -> Json.fromString("POST"),
        "type" -> Json.fromString("AWS_PROXY"),
        "uri" -> Json.fromString(s"aws_lambda_function.$lambdaFunctionResourceName.invoke_arn")
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
        "resource_id" -> Json.fromString("aws_api_gateway_method.proxy_root.resource_id"),
        "http_method" -> Json.fromString("aws_api_gateway_method.proxy_root.http_method"),
        "integration_http_method" -> Json.fromString("POST"),
        "type" -> Json.fromString("AWS_PROXY"),
        "uri" -> Json.fromString(s"aws_lambda_function.$lambdaFunctionResourceName.invoke_arn")
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

    _ =>
      Json.fromFields(
        Seq(
          "resource" -> Json.fromValues(
            Seq(
              resource("aws_api_gateway_rest_api", s"${options.appName}", apiGatewayRestApi),
              // resources below are responsible for forwarding any incoming request to lambda function
              resource("aws_api_gateway_resource", "proxy", apiGatewayResource),
              resource("aws_api_gateway_method", "proxy", apiGatewayMethodProxy),
              resource("aws_api_gateway_integration", "lambda", apiGatewayIntegration),
              resource("aws_api_gateway_method", "proxy_root", apiGatewayMethodProxyRoot),
              resource("aws_api_gateway_integration", "lambda_root", apiGatewayIntegrationRoot),
              //
              resource("aws_api_gateway_deployment", s"${options.appName}", apiGatewayDeployment)
            )
          )
        )
      )
  }

  private def resource[R](`type`: String, name: String, encoded: Json): Json =
    Json.fromFields(
      Seq(
        `type` -> Json.fromFields(
          Seq(
            name -> encoded
          )
        )
      )
    )
}
