package sttp.tapir.serverless.aws.sam

import sttp.model.Method
import sttp.tapir.internal._
import sttp.tapir.{AnyEndpoint, EndpointInput}

import scala.collection.immutable.SortedMap

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

    val parameters = options.parameters.map(parameters => SortedMap.from(parameters.map(Parameter.apply).toList))
    val auths = {
      for {
        httpApi <- options.httpApi
        auths <- httpApi.auths
        models = auths.auths.map(authorizerToModel).toSeq.toMap
      } yield HttpApiAuth(
        Authorizers = models,
        DefaultAuthorizer = auths.default,
        EnableIamAuthorizer = None
      )
    }

    SamTemplate(
      Parameters = parameters,
      Resources = Map(
        functionName -> FunctionResource(
          options.source match {
            case ImageSource(imageUri) =>
              FunctionImageProperties(options.timeout.toSeconds, options.memorySize, apiEvents, imageUri)
            case cs @ CodeSource(_, _, _, environment, role) =>
              FunctionCodeProperties(
                options.timeout.toSeconds,
                options.memorySize,
                apiEvents,
                cs.runtime,
                cs.codeUri,
                cs.handler,
                Environment = if (environment.nonEmpty) Some(EnvironmentCodeProperties(environment)) else None,
                Role = role
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
              ),
            Auth = auths
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

  private def authorizerToModel(auth: HttpApiProperties.Auth): (String, Authorizer) = auth match {
    case auth: HttpApiProperties.Auth.Lambda =>
      val authorizer = LambdaAuthorizer(
        AuthorizerPayloadFormatVersion = if (auth.version == HttpApiProperties.Auth.Version.V1) "1.0" else "2.0",
        EnableFunctionDefaultPermissions = Some(auth.enableDefaultPermissions),
        EnableSimpleResponses = auth.version match {
          case HttpApiProperties.Auth.Version.V2Simple    => Some(true)
          case HttpApiProperties.Auth.Version.V2IamPolicy => Some(false)
          case HttpApiProperties.Auth.Version.V1          => None
        },
        FunctionArn = auth.functionArn,
        FunctionInvokeRole = auth.functionRole,
        Identity = Option.when(auth.identity.nonEmpty)(
          LambdaAuthorizationIdentity(
            Context = None,
            Headers = auth.identity.headers.map(_.toSeq),
            QueryStrings = auth.identity.queryStrings.map(_.toSeq),
            ReauthorizeEvery = auth.identity.reauthorizeEvery,
            StageVariables = None
          )
        )
      )
      auth.name -> authorizer
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
