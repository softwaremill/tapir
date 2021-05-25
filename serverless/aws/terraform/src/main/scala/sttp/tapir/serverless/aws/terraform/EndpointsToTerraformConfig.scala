package sttp.tapir.serverless.aws.terraform

import sttp.model.Method
import sttp.tapir.internal._
import sttp.tapir.serverless.aws.terraform.PathComponent.DefaultParamName
import sttp.tapir.{Endpoint, EndpointInput, _}

import java.util.UUID

private[terraform] object EndpointsToTerraformConfig {
  type IdMethodEndpointInput = (String, Method, EndpointInput.Basic[_])

  private val headerPrefix = "method.request.header."
  private val queryPrefix = "method.request.querystring."
  private val pathPrefix = "method.request.path."

  def apply(eps: List[Endpoint[_, _, _, _]])(implicit options: AwsTerraformOptions): AwsTerraformApiGateway = {
    val epsBasicInputs: Seq[(Endpoint[_, _, _, _], Vector[IdMethodEndpointInput])] = eps.map { ep =>
      ep -> ep.input.asVectorOfBasicInputs().map(input => (UUID.randomUUID().toString, ep.httpMethod.getOrElse(Method("ANY")), input))
    }

    val resourceTree = ApiResourceTree(epsBasicInputs.map { case (_, bi) => bi })

    val methods: Seq[AwsTerraformApiGatewayMethod] = epsBasicInputs.map { case (endpoint, bi) =>
      val method = endpoint.httpMethod.getOrElse(Method("ANY"))

      val pathComponents: Seq[(PathComponent, String)] = bi
        .foldLeft((Seq.empty[(PathComponent, String)], 0)) { case ((acc, c), input) =>
          input match {
            case (id, m, fp @ EndpointInput.FixedPath(p, _, _)) => (acc :+ PathComponent(id, m, Left(fp)) -> p, c)
            case (id, m, pc @ EndpointInput.PathCapture(name, _, _)) =>
              (acc :+ PathComponent(id, m, Right(pc)) -> name.getOrElse(s"$DefaultParamName$c"), if (name.isEmpty) c + 1 else c)
            case _ => (acc, c)
          }
        }
        ._1

      val path = pathComponents
        .map {
          case (PathComponent(_, _, Left(_)), p)  => p
          case (PathComponent(_, _, Right(_)), p) => s"{$p}"
        }
        .mkString("/")

      val nameComponents = if (pathComponents.isEmpty) Vector("root") else pathComponents.map { case (_, name) => name }
      val name = s"${method.method.toLowerCase.capitalize}${nameComponents.map(_.toLowerCase.capitalize).mkString}"

      val requestParameters: Seq[(String, Boolean)] = bi.collect {
        case (_, _, EndpointIO.Header(name, codec, _))        => s"$headerPrefix$name" -> !codec.schema.isOptional
        case (_, _, EndpointIO.FixedHeader(header, codec, _)) => s"$headerPrefix${header.name}" -> !codec.schema.isOptional
        case (_, _, EndpointInput.Query(name, codec, _))      => s"$queryPrefix$name" -> !codec.schema.isOptional
      } ++ pathComponents.collect { case (c @ PathComponent(_, _, Right(pc)), _) => s"$pathPrefix${c.name}" -> !pc.codec.schema.isOptional }

      AwsTerraformApiGatewayMethod(
        name,
        path,
        method,
        pathComponents.map { case (pc, _) => pc },
        requestParameters
      )
    }

    AwsTerraformApiGateway(resourceTree, methods)
  }
}

case class PathComponent(id: String, method: Method, component: Either[EndpointInput.FixedPath[_], EndpointInput.PathCapture[_]]) {
  def name: String = component match {
    case Left(fp)  => fp.s
    case Right(pc) => pc.name.getOrElse(DefaultParamName)
  }
}

object PathComponent {
  val DefaultParamName = "param"
}
