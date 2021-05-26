package sttp.tapir.serverless.aws.terraform

import sttp.model.Method
import sttp.tapir.internal._
import sttp.tapir.serverless.aws.terraform.PathComponent.DefaultParamName
import sttp.tapir.{Endpoint, EndpointInput, _}

private[terraform] object EndpointsToTerraformConfig {
  type IdMethodEndpointInput = (String, Method, EndpointInput.Basic[_])

  private val headerPrefix = "method.request.header."
  private val queryPrefix = "method.request.querystring."
  private val pathPrefix = "method.request.path."

  def apply(eps: List[Endpoint[_, _, _, _]])(implicit options: AwsTerraformOptions): AwsTerraformApiGateway = {

    val methods: Seq[AwsApiGatewayRoute] = eps.map { endpoint =>
      val method = endpoint.httpMethod.getOrElse(Method("ANY"))

      val basicInputs = endpoint.input.asVectorOfBasicInputs()

      val pathComponents: Seq[(Either[EndpointInput.FixedPath[_], EndpointInput.PathCapture[_]], String)] = basicInputs
        .foldLeft((Seq.empty[(Either[EndpointInput.FixedPath[_], EndpointInput.PathCapture[_]], String)], 0)) { case ((acc, c), input) =>
          input match {
            case fp @ EndpointInput.FixedPath(p, _, _) => (acc :+ Left(fp) -> p, c)
            case pc @ EndpointInput.PathCapture(name, _, _) =>
              (acc :+ Right(pc) -> name.getOrElse(s"$DefaultParamName$c"), if (name.isEmpty) c + 1 else c)
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

      val requestParameters: Seq[(String, Boolean)] = basicInputs.collect {
        case EndpointIO.Header(name, codec, _)        => s"$headerPrefix$name" -> !codec.schema.isOptional
        case EndpointIO.FixedHeader(header, codec, _) => s"$headerPrefix${header.name}" -> !codec.schema.isOptional
        case EndpointInput.Query(name, codec, _)      => s"$queryPrefix$name" -> !codec.schema.isOptional
      } ++ pathComponents.collect { case c @ (Right(pc), name) => s"$pathPrefix${name}" -> !pc.codec.schema.isOptional }

      AwsApiGatewayRoute(
        name,
        path,
        method,
        Seq.empty
      )
    }

    AwsTerraformApiGateway(methods)
  }
}

object PathComponent {
  val DefaultParamName = "param"
}
