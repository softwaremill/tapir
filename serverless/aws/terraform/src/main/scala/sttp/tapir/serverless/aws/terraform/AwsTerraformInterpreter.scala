package sttp.tapir.serverless.aws.terraform

import sttp.tapir.{AnyEndpoint, Endpoint}
import sttp.tapir.server.ServerEndpoint

trait AwsTerraformInterpreter {

  def awsTerraformOptions: AwsTerraformOptions

  def toTerraformConfig[I, E, O, S](e: Endpoint[I, E, O, S]): AwsTerraformApiGateway =
    EndpointsToTerraformConfig(List(e), awsTerraformOptions)

  def toTerraformConfig(es: Iterable[AnyEndpoint]): AwsTerraformApiGateway =
    EndpointsToTerraformConfig(es.toList, awsTerraformOptions)

  def toTerraformConfig[I, E, O, S, F[_]](se: ServerEndpoint[I, E, O, S, F]): AwsTerraformApiGateway =
    EndpointsToTerraformConfig(
      List(se.endpoint),
      awsTerraformOptions
    )

  def serverEndpointsToTerraformConfig[F[_]](ses: Iterable[ServerEndpoint[_, _, _, _, F]]): AwsTerraformApiGateway =
    EndpointsToTerraformConfig(ses.map(_.endpoint).toList, awsTerraformOptions)
}

object AwsTerraformInterpreter {
  def apply(terraformOptions: AwsTerraformOptions): AwsTerraformInterpreter = {
    new AwsTerraformInterpreter {
      override def awsTerraformOptions: AwsTerraformOptions = terraformOptions
    }
  }
}
