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

      def toTerraformResources(parent: String, tree: ResourceTree): Seq[(String, AwsApiGatewayResource)] = {
        val name = tree.path.name
        val resourceName = if (parent == TapirApiGateway) name else s"$parent-$name"
        val apiGatewayResource =
          AwsApiGatewayResource(resourceName, parentId = parent, tree.path.component.map(_ => s"{$name}").getOrElse(name))

        if (tree.children.isEmpty)
          Seq(tree.path.id -> apiGatewayResource)
        else
          (tree.path.id -> apiGatewayResource) +: tree.children.flatMap(child => toTerraformResources(parent = resourceName, child))
      }

      val pathResources: Seq[(String, AwsApiGatewayResource)] =
        gateway.resourceTree.children.flatMap(toTerraformResources(TapirApiGateway, _))

      val methodResources: Seq[TerraformResource] =
        gateway.methods
          .flatMap { m =>
            val resourceId =
              pathResources
                .find { case (id, _) => m.paths.lastOption.map(_.id).getOrElse(TapirApiGateway) == id }
                .map { case (_, res) => res.name }
                .getOrElse(TapirApiGateway)
            Seq(
              AwsApiGatewayMethod(m.name, resourceId, m.httpMethod.method, m.requestParameters),
              AwsApiGatewayIntegration(m.name)
            )
          }

      val output = Json.fromFields(
        Seq(
          "base_url" -> Json.fromFields(
            Seq("value" -> Json.fromString(s"$${aws_api_gateway_deployment.$TapirApiGateway.invoke_url}"))
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
              AwsApiGatewayRestApi(options.apiGatewayName, options.apiGatewayDescription).json(),
              AwsApiGatewayDeployment(methodResources.collect { case i @ AwsApiGatewayIntegration(_) => i.name }).json()
            )
              ++ pathResources
                .groupBy { case (_, res) => res.name }
                .flatMap { case (_, res) => res.headOption }
                .toSeq
                .sortBy { case (_, res) => res.name.length }
                .map { case (_, res) => res.json() }
              ++ methodResources.map(_.json())
          ),
          "output" -> output
        )
      )
    }
}
