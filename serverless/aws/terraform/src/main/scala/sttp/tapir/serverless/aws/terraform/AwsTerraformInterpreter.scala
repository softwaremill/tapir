package sttp.tapir.serverless.aws.terraform

import sttp.tapir.{AnyEndpoint, Endpoint}
import sttp.tapir.server.ServerEndpoint

trait AwsTerraformInterpreter {

  def toTerraformConfig[A, I, E, O, R](e: Endpoint[A, I, E, O, R]): AwsTerraformApiGateway =
    EndpointsToTerraformConfig(List(e))

  def toTerraformConfig(es: Iterable[AnyEndpoint]): AwsTerraformApiGateway =
    EndpointsToTerraformConfig(es.toList)

  def toTerraformConfig[R, F[_]](se: ServerEndpoint[R, F]): AwsTerraformApiGateway =
    EndpointsToTerraformConfig(
      List(se.endpoint)
    )

  def serverEndpointsToTerraformConfig[F[_]](ses: Iterable[ServerEndpoint[_, F]]): AwsTerraformApiGateway =
    EndpointsToTerraformConfig(ses.map(_.endpoint).toList)
}

object AwsTerraformInterpreter {
  def apply(): AwsTerraformInterpreter = new AwsTerraformInterpreter {}
}
