package sttp.tapir.serverless.aws.terraform

import sttp.model.Method
import sttp.tapir.internal._
import sttp.tapir.{AnyEndpoint, EndpointInput}

private[terraform] object EndpointsToTerraformConfig {
  def apply(eps: List[AnyEndpoint]): AwsTerraformApiGateway = {

    val routes: Seq[AwsApiGatewayRoute] = eps.map { endpoint =>
      val method = endpoint.method.getOrElse(Method("ANY"))

      val basicInputs = endpoint.asVectorOfBasicInputs()

      val pathComponents: Seq[(Either[EndpointInput.FixedPath[_], EndpointInput.PathCapture[_]], String)] = basicInputs
        .foldLeft((Seq.empty[(Either[EndpointInput.FixedPath[_], EndpointInput.PathCapture[_]], String)], 0)) { case ((acc, c), input) =>
          input match {
            case fp @ EndpointInput.FixedPath(p, _, _)      => (acc :+ Left(fp) -> p, c)
            case pc @ EndpointInput.PathCapture(name, _, _) =>
              (acc :+ Right(pc) -> name.getOrElse(s"param$c"), if (name.isEmpty) c + 1 else c)
            case _ => (acc, c)
          }
        }
        ._1

      val path = pathComponents
        .map {
          case (Left(_), p)  => p
          case (Right(_), p) => s"{$p}"
        }
        .mkString("/")

      val nameComponents = if (pathComponents.isEmpty) Vector("root") else pathComponents.map { case (_, name) => name }
      val name = s"${method.method.toLowerCase.capitalize}${nameComponents.map(_.toLowerCase.capitalize).mkString}"

      AwsApiGatewayRoute(name, path, method)
    }

    AwsTerraformApiGateway(routes)
  }
}
