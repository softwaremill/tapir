package sttp.tapir.serverless.aws.sam

import io.circe.syntax._
import sttp.tapir.serverless.aws.sam.AwsSamTemplateEncoders._

case class SamTemplate(
    AWSTemplateFormatVersion: String = "2010-09-09",
    Transform: String = "AWS::Serverless-2016-10-31",
    Resources: Map[String, Resource],
    Outputs: Map[String, Output]
) {
  def toYaml: String = Printer(dropNullKeys = true, preserveOrder = true, stringStyle = Printer.StringStyle.Plain).pretty(this.asJson)
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
    Handler: String
) extends Properties
    with FunctionProperties

case class HttpProperties(StageName: String, CorsConfiguration: Option[CorsConfiguration]) extends Properties

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
    AllowHeaders: List[String],
    AllowMethods: List[String],
    AllowOrigins: List[String],
    ExposeHeaders: Option[List[String]],
    MaxAge: Option[Long]
)
