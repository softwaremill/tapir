package sttp.tapir.serverless.aws.terraform

import sttp.tapir.internal._
import sttp.tapir.{Endpoint, EndpointInput, _}

import java.util.UUID

private[terraform] object EndpointsToTerraformConfig {
  type IdEndpointInput = (String, EndpointInput.Basic[_])

  private val headerPrefix = "method.request.header."
  private val queryPrefix = "method.request.querystring."
  private val pathPrefix = "method.request.path."

  def apply(eps: List[Endpoint[_, _, _, _]])(implicit options: AwsTerraformOptions): AwsTerraformApiGateway = {
    val epsBasicInputs: Seq[(Endpoint[_, _, _, _], Vector[IdEndpointInput])] = eps.map { ep =>
      ep -> ep.input.asVectorOfBasicInputs().map(input => UUID.randomUUID().toString -> input)
    }

    val resourceTree = ApiResourceTree(epsBasicInputs.map { case (_, bi) => bi })

    val methods: Seq[AwsTerraformApiGatewayMethod] = epsBasicInputs.map { case (endpoint, bi) =>
      val pathComponents: Seq[(PathComponent, String)] = bi
        .foldLeft((Seq.empty[(PathComponent, String)], 0)) { case ((acc, c), input) =>
          input match {
            case (id, fp @ EndpointInput.FixedPath(p, _, _)) => (acc :+ PathComponent(id, Left(fp)) -> p, c)
            case (id, pc @ EndpointInput.PathCapture(name, _, _)) =>
              (acc :+ PathComponent(id, Right(pc)) -> name.getOrElse(s"param$c"), if (name.isEmpty) c + 1 else c)
            case _ => (acc, c)
          }
        }
        ._1

      val path = pathComponents
        .map {
          case (PathComponent(_, Left(_)), p)  => p
          case (PathComponent(_, Right(_)), p) => s"{$p}"
        }
        .mkString("/")

      val method = endpoint.httpMethod

      val nameComponents = if (pathComponents.isEmpty) Vector("root") else pathComponents.map { case (_, name) => name }
      val name = (method.map(_.method.toLowerCase).getOrElse("any").capitalize +: nameComponents.map(_.toLowerCase.capitalize)).mkString

      val requestParameters: Seq[(String, Boolean)] = bi.collect {
        case (_, EndpointIO.Header(name, codec, _))        => s"$headerPrefix$name" -> !codec.schema.isOptional
        case (_, EndpointIO.FixedHeader(header, codec, _)) => s"$headerPrefix${header.name}" -> !codec.schema.isOptional
        case (_, EndpointInput.Query(name, codec, _))      => s"$queryPrefix$name" -> !codec.schema.isOptional
      } ++ pathComponents.collect { case (_ @PathComponent(_, Right(pc)), _) =>
        s"$pathPrefix${pc.name.getOrElse("param")}" -> !pc.codec.schema.isOptional
      }

      AwsTerraformApiGatewayMethod(
        name,
        path,
        method.map(_.method).getOrElse("ANY"),
        pathComponents.map { case (pc, _) => pc },
        requestParameters
      )
    }

    AwsTerraformApiGateway(resourceTree, methods)
  }
}

case class PathComponent(id: String, component: Either[EndpointInput.FixedPath[_], EndpointInput.PathCapture[_]]) {
  def name: String = component match {
    case Left(fp)  => fp.s
    case Right(pc) => pc.name.getOrElse("param")
  }
}
