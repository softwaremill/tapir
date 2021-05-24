package sttp.tapir.serverless.aws.terraform

import io.circe.{Encoder, Json}
import sttp.tapir.serverless.aws.terraform.ApiResourceTree.RootPath

object AwsTerraformEncoders {

  implicit def encoderAwsTerraformOptions(implicit options: AwsTerraformOptions): Encoder[AwsTerraformOptions] = {

    val provider = Json.fromFields(Seq("aws" -> Json.fromValues(Seq(Json.fromFields(Seq("region" -> Json.fromString(options.awsRegion)))))))
    val terraform = Json.fromFields(
      Seq("required_providers" -> Json.fromFields(Seq("aws" -> Json.fromFields(Seq("source" -> Json.fromString("hashicorp/aws"))))))
    )

    val lambdaFunction = AwsLambdaFunction(options.functionName, options.timeout, options.memorySize, options.functionSource)
    val iAmRole = AwsIamRole(options.assumeRolePolicy.noSpaces)
    val permission = AwsLambdaPermission(options.apiGatewayName)

    _ =>
      Json.fromFields(
        Seq(
          "terraform" -> terraform,
          "provider" -> provider,
          "resource" -> Json.fromValues(
            Seq(
              lambdaFunction.json(),
              iAmRole.json(),
              permission.json()
            )
          )
        )
      )
  }

  implicit def encoderAwsTerraformApiGateway(implicit options: AwsTerraformOptions): Encoder[AwsTerraformApiGateway] =
    gateway => {

      def toTerraformResources(parent: String, resource: ResourceTree): Seq[TerraformResource] = {
        val name = resource.pathComponent.name
        val resourceName = if (parent == RootPath) name else s"$parent-$name"
        val apiGatewayResource = AwsApiGatewayResource(
          resourceName,
          options.apiGatewayName,
          if (parent == RootPath) options.apiGatewayName else parent,
          resource.pathComponent.component.map(_ => s"{$name}").getOrElse(name)
        )

        if (resource.children.isEmpty) {
          // reached the leaf - last path component of single endpoint
          // integration resource has to be linked to last path component resource
          val methodIntegrationResources =
            gateway.methods
              .find(_.pathComponents.lastOption.exists(_.id == resource.pathComponent.id))
              .map { method =>
                Seq(
                  AwsApiGatewayMethod(method.name, options.apiGatewayName, resourceName, method.httpMethod, method.requestParameters),
                  AwsApiGatewayIntegration(method.name, options.apiGatewayName)
                )
              }
              .getOrElse(Seq.empty)

          Seq(apiGatewayResource) ++ methodIntegrationResources

        } else Seq(apiGatewayResource) ++ resource.children.flatMap(child => toTerraformResources(parent = resourceName, child))
      }

      val pathComponentResources: Seq[TerraformResource] =
        gateway.resourceTree.children.flatMap(child => toTerraformResources(RootPath, child))

      val apiGatewayDeployment = AwsApiGatewayDeployment(
        options.apiGatewayName,
        options.apiGatewayName,
        pathComponentResources.collect { case i @ AwsApiGatewayIntegration(_, _) => i }
      )

      val output = Json.fromFields(
        Seq(
          "base_url" -> Json.fromFields(
            Seq("value" -> Json.fromString(s"$${aws_api_gateway_deployment.${options.apiGatewayName}.invoke_url}"))
          )
        )
      )

      Json.fromFields(
        Seq(
          "resource" -> Json.fromValues(
            Seq(
              AwsApiGatewayRestApi(options.apiGatewayName, "description").json(),
              apiGatewayDeployment.json()
            ) ++ pathComponentResources.map(_.json())
          ),
          "output" -> output
        )
      )
    }
}
