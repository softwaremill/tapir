package sttp.tapir.serverless.aws.terraform

case class AwsTerraformApiGateway(resourceTree: ResourceTree, methods: Seq[AwsTerraformApiGatewayMethod])
case class AwsTerraformApiGatewayMethod(
    name: String,
    path: String,
    httpMethod: String,
    pathComponents: Seq[PathComponent],
    requestParameters: Seq[(String, Boolean)]
)
