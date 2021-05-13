package sttp.tapir.serverless.aws.sam

import sttp.model.Method
import sttp.tapir.internal._
import sttp.tapir.{Endpoint, EndpointInput}

import scala.collection.immutable.ListMap

private[sam] object EndpointsToSamTemplate {
  def apply(es: List[Endpoint[_, _, _, _]])(implicit options: AwsSamOptions): SamTemplate = {
    val functionName = options.namePrefix + "Function"
    val httpApiName = options.namePrefix + "HttpApi"

    val apiEvents = es.map(endpointNameMethodAndPath).map { case (name, method, path) =>
      name -> FunctionHttpApiEvent(
        FunctionHttpApiEventProperties(s"!Ref $httpApiName", method.map(_.method).getOrElse("ANY"), path, options.timeout.toMillis)
      )
    }

    SamTemplate(
      Resources = ListMap(
        functionName -> FunctionResource(
          options.source match {
            case ImageSource(imageUri) =>
              FunctionImageProperties(options.timeout.toSeconds, options.memorySize, ListMap.from(apiEvents), imageUri)
            case cs @ CodeSource(_, _, _) =>
              FunctionCodeProperties(
                options.timeout.toSeconds,
                options.memorySize,
                ListMap.from(apiEvents),
                cs.runtime,
                cs.codeUri,
                cs.handler
              )
          }
        ),
        httpApiName -> HttpResource(HttpProperties("$default"))
      ),
      Outputs = ListMap(
        (options.namePrefix + "Url") -> Output(
          "Base URL of your endpoints",
          ListMap("Fn::Sub" -> ("https://${" + httpApiName + "}.execute-api.${AWS::Region}.${AWS::URLSuffix}"))
        )
      )
    )
  }

  private def endpointNameMethodAndPath(e: Endpoint[_, _, _, _]): (String, Option[Method], String) = {
    val pathComponents = e.input
      .asVectorOfBasicInputs()
      .collect {
        case EndpointInput.PathCapture(name, _, _) => Left(name)
        case EndpointInput.FixedPath(s, _, _)      => Right(s)
      }
      .foldLeft((Vector.empty[Either[String, String]], 0)) { case ((acc, c), component) =>
        component match {
          case Left(None)    => (acc :+ Left(s"param$c"), c + 1)
          case Left(Some(p)) => (acc :+ Left(p), c)
          case Right(p)      => (acc :+ Right(p), c)
        }
      }
      ._1

    val method = e.httpMethod

    val nameComponents = if (pathComponents.isEmpty) Vector("root") else pathComponents.map(_.fold(identity, identity))
    val name = (method.map(_.method.toLowerCase).getOrElse("any").capitalize +: nameComponents.map(_.toLowerCase.capitalize)).mkString

    val idComponents = pathComponents.map {
      case Left(s)  => s"{$s}"
      case Right(s) => s
    }

    (name, method, "/" + idComponents.mkString("/"))
  }
}
