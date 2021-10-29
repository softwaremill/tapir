package sttp.tapir.serverless.aws.terraform

import sttp.tapir.{AnyEndpoint, Endpoint}
import sttp.tapir.server.ServerEndpoint

trait AwsTerraformInterpreter {

  def toTerraformConfig[A, I, E, O, S](e: Endpoint[A, I, E, O, S]): AwsTerraformApiGateway =
    EndpointsToTerraformConfig(List(e))

  def toTerraformConfig(es: Iterable[AnyEndpoint]): AwsTerraformApiGateway =
    EndpointsToTerraformConfig(es.toList)

  def toTerraformConfig[A, U, I, E, O, S, F[_]](se: ServerEndpoint[A, U, I, E, O, S, F]): AwsTerraformApiGateway =
    EndpointsToTerraformConfig(
      List(se.endpoint)
    )

  def serverEndpointsToTerraformConfig[F[_]](ses: Iterable[ServerEndpoint[_, _, _, _, _, _, F]]): AwsTerraformApiGateway =
    EndpointsToTerraformConfig(ses.map(_.endpoint).toList)
}

object AwsTerraformInterpreter {
  def apply(): AwsTerraformInterpreter = new AwsTerraformInterpreter {}
}
