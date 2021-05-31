package sttp.tapir.serverless.aws.terraform

import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint

trait AwsTerraformInterpreter {
  def toTerraformConfig[I, E, O, S](e: Endpoint[I, E, O, S])(implicit options: AwsTerraformOptions): AwsTerraformApiGateway =
    EndpointsToTerraformConfig(List(e))

  def toTerraformConfig(es: Iterable[Endpoint[_, _, _, _]])(implicit options: AwsTerraformOptions): AwsTerraformApiGateway =
    EndpointsToTerraformConfig(es.toList)

  def toTerraformConfig[I, E, O, S, F[_]](se: ServerEndpoint[I, E, O, S, F])(implicit
      options: AwsTerraformOptions
  ): AwsTerraformApiGateway =
    EndpointsToTerraformConfig(
      List(se.endpoint)
    )

  def serverEndpointsToTerraformConfig[F[_]](ses: Iterable[ServerEndpoint[_, _, _, _, F]])(implicit
      options: AwsTerraformOptions
  ): AwsTerraformApiGateway =
    EndpointsToTerraformConfig(ses.map(_.endpoint).toList)
}

object AwsTerraformInterpreter extends AwsTerraformInterpreter
