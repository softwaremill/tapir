package sttp.tapir.serverless.aws.sam

import sttp.model.Method
import sttp.tapir.internal._
import sttp.tapir.{AnyEndpoint, EndpointInput}

private[sam] object EndpointsToSamTemplate {
  def apply(es: List[AnyEndpoint], options: AwsSamOptions): SamTemplate = {
    val functionName = options.namePrefix + "Function"
    val httpApiName = options.namePrefix + "HttpApi"

    val apiEvents = es
      .map(endpointNameMethodAndPath)
      .map { case (name, method, path) =>
        name -> FunctionHttpApiEvent(
          FunctionHttpApiEventProperties(s"!Ref $httpApiName", method.map(_.method).getOrElse("ANY"), path, options.timeout.toMillis)
        )
      }
      .toMap

    SamTemplate(
      Resources = Map(
        functionName -> FunctionResource(
          options.source match {
            case ImageSource(imageUri) =>
              FunctionImageProperties(options.timeout.toSeconds, options.memorySize, apiEvents, imageUri)
            case cs @ CodeSource(_, _, _, environment) =>
              FunctionCodeProperties(
                options.timeout.toSeconds,
                options.memorySize,
                apiEvents,
                cs.runtime,
                cs.codeUri,
                cs.handler,
                Environment = if (environment.nonEmpty) Some(EnvironmentCodeProperties(environment)) else None
              )
          }
        ),
        httpApiName -> HttpResource(
          HttpProperties(
            "$default",
            CorsConfiguration = options.httpApi
              .flatMap(_.cors)
              .map(v =>
                CorsConfiguration(
                  AllowCredentials = v.allowCredentials.map(_ == HttpApiProperties.AllowedCredentials.Allow),
                  AllowHeaders = v.allowedHeaders.map {
                    case HttpApiProperties.AllowedHeaders.All                => Set("*")
                    case HttpApiProperties.AllowedHeaders.Some(headersNames) => headersNames
                  },
                  AllowMethods = v.allowedMethods.map {
                    case HttpApiProperties.AllowedMethods.All           => Set("*")
                    case HttpApiProperties.AllowedMethods.Some(methods) => methods.map(_.method)
                  },
                  AllowOrigins = v.allowedOrigins.map {
                    case HttpApiProperties.AllowedOrigin.All           => Set("*")
                    case HttpApiProperties.AllowedOrigin.Some(origins) => origins.map(_.toString)
                  },
                  ExposeHeaders = v.exposeHeaders.map {
                    case HttpApiProperties.ExposedHeaders.All               => Set("*")
                    case HttpApiProperties.ExposedHeaders.Some(headerNames) => headerNames
                  },
                  MaxAge = v.maxAge.map { case HttpApiProperties.MaxAge.Some(duration) => duration.toSeconds }
                )
              )
          )
        )
      ),
      Outputs = Map(
        (options.namePrefix + "Url") -> Output(
          "Base URL of your endpoints",
          Map("Fn::Sub" -> ("https://${" + httpApiName + "}.execute-api.${AWS::Region}.${AWS::URLSuffix}"))
        )
      )
    )
  }

  private def endpointNameMethodAndPath(e: AnyEndpoint): (String, Option[Method], String) = {
    val pathComponents = e
      .asVectorOfBasicInputs()
      .foldLeft((Vector.empty[Either[String, String]], 0)) { case ((acc, c), input) =>
        input match {
          case EndpointInput.PathCapture(name, _, _) => (acc :+ Left(name.getOrElse(s"param$c")), if (name.isEmpty) c + 1 else c)
          case EndpointInput.FixedPath(p, _, _)      => (acc :+ Right(p), c)
          case _                                     => (acc, c)
        }
      }
      ._1

    val method = e.method

    val nameComponents = if (pathComponents.isEmpty) Vector("root") else pathComponents.map(_.fold(identity, identity))
    val name = (method.map(_.method.toLowerCase).getOrElse("any").capitalize +: nameComponents.map(
      _.toLowerCase.capitalize.replaceAll("\\W", "")
    )).mkString

    val idComponents = pathComponents.map {
      case Left(s)  => s"{$s}"
      case Right(s) => s
    }

    (name, method, "/" + idComponents.mkString("/"))
  }
}
