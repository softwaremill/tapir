package sttp.tapir.serverless.aws.sam

import io.circe.syntax._
import sttp.tapir.serverless.aws.sam.AwsSamTemplateEncoders._
import sttp.tapir.serverless.aws.sam.parameter.InputParameter

case class SamTemplate(
    AWSTemplateFormatVersion: String = "2010-09-09",
    Transform: String = "AWS::Serverless-2016-10-31",
    Parameters: Option[Map[String, Parameter]] = None,
    Resources: Map[String, Resource],
    Outputs: Map[String, Output]
) {
  def toYaml: String = Printer(dropNullKeys = true, preserveOrder = true, stringStyle = Printer.StringStyle.Plain).pretty(this.asJson)
}

case class Parameter(Description: Option[String])
object Parameter {
  def apply(in: InputParameter): (String, Parameter) =
    in.name -> Parameter(in.description)
}

sealed trait Resource {
  def Properties: Properties
}
case class FunctionResource(Properties: Properties) extends Resource
case class HttpResource(Properties: HttpProperties) extends Resource

sealed trait Properties

sealed trait FunctionProperties {
  val Timeout: Long
  val MemorySize: Int
  val Events: Map[String, FunctionHttpApiEvent]
}

case class FunctionImageProperties(
    Timeout: Long,
    MemorySize: Int,
    Events: Map[String, FunctionHttpApiEvent],
    ImageUri: String,
    PackageType: String = "Image"
) extends Properties
    with FunctionProperties

case class FunctionCodeProperties(
    Timeout: Long,
    MemorySize: Int,
    Events: Map[String, FunctionHttpApiEvent],
    Runtime: String,
    CodeUri: String,
    Handler: String,
    Environment: Option[EnvironmentCodeProperties],
    Role: Option[String] = None
) extends Properties
    with FunctionProperties

case class HttpProperties(StageName: String, CorsConfiguration: Option[CorsConfiguration], Auth: Option[HttpApiAuth] = None)
    extends Properties

case class FunctionHttpApiEvent(Properties: FunctionHttpApiEventProperties)

case class FunctionHttpApiEventProperties(
    ApiId: String,
    Method: String,
    Path: String,
    TimeoutInMillis: Long,
    PayloadFormatVersion: String = "2.0"
)

case class Output(Description: String, Value: Map[String, String])

case class CorsConfiguration(
    AllowCredentials: Option[Boolean],
    AllowHeaders: Option[Set[String]],
    AllowMethods: Option[Set[String]],
    AllowOrigins: Option[Set[String]],
    ExposeHeaders: Option[Set[String]],
    MaxAge: Option[Long]
)

case class EnvironmentCodeProperties(Variables: Map[String, String])

case class HttpApiAuth(
    Authorizers: Map[String, Authorizer],
    DefaultAuthorizer: Option[String],
    EnableIamAuthorizer: Option[Boolean]
)

sealed trait Authorizer

case class OAuth2Authorizer(
    AuthorizationScopes: Option[Seq[String]],
    IdentitySource: Option[String],
    JwtConfiguration: JwtConfiguration
) extends Authorizer

case class JwtConfiguration(
    Audience: Option[Seq[String]],
    Issuer: Option[String]
)

case class LambdaAuthorizer(
    AuthorizerPayloadFormatVersion: String,
    EnableFunctionDefaultPermissions: Option[Boolean],
    EnableSimpleResponses: Option[Boolean],
    FunctionArn: String,
    FunctionInvokeRole: Option[String],
    Identity: Option[LambdaAuthorizationIdentity]
) extends Authorizer

case class LambdaAuthorizationIdentity(
    Context: Option[Seq[String]],
    Headers: Option[Seq[String]],
    QueryStrings: Option[Seq[String]],
    ReauthorizeEvery: Option[Int],
    StageVariables: Option[Seq[String]]
)
