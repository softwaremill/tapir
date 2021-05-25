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

      def toTerraformResources(parent: String, tree: ResourceTree, acc: Map[String, AwsApiGatewayResource]): Seq[TerraformResource] = {
        println(acc.size)

        val name = tree.path.name
        val resourceName = if (parent == TapirApiGateway) name else s"$parent-$name"
        val apiGatewayResource =
          AwsApiGatewayResource(resourceName, parentId = parent, tree.path.component.map(_ => s"{$name}").getOrElse(name))

        val methodResources =
          gateway.methods
            .find(_.paths.lastOption.exists(_.id == tree.path.id))
            .map { m =>
              val resourceId = acc.getOrElse(m.paths.last.id, apiGatewayResource).name
              Seq(
                AwsApiGatewayMethod(m.name, resourceId, m.httpMethod.method, m.requestParameters),
                AwsApiGatewayIntegration(m.name)
              )
            }
            .getOrElse(Seq.empty)

        if (tree.children.isEmpty)
          methodResources ++ (apiGatewayResource +: acc.values.groupBy(_.pathPart).flatMap(_._2.headOption).toSeq)
        else
          methodResources ++ tree.children.flatMap(child => toTerraformResources(parent = resourceName, child, acc + (tree.path.id -> apiGatewayResource)))
      }

      val endpointResources: Seq[TerraformResource] =
        gateway.resourceTree.children.flatMap(child => toTerraformResources(parent = TapirApiGateway, child, Map.empty))

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
              AwsApiGatewayDeployment(endpointResources.collect { case i @ AwsApiGatewayIntegration(_) => i.name }).json()
            ) ++ endpointResources.map(_.json())
          ),
          "output" -> output
        )
      )
    }
}
