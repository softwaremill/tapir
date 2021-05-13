package sttp.tapir.serverless.aws.sam

import io.circe.syntax._
import sttp.tapir.serverless.aws.sam.AwsSamTemplateEncoders._

import scala.collection.immutable.ListMap

case class SamTemplate(
    AWSTemplateFormatVersion: String = "2010-09-09",
    Transform: String = "AWS::Serverless-2016-10-31",
    Resources: ListMap[String, Resource],
    Outputs: ListMap[String, Output]
) {
  def toYaml: String = {
    val template: SamTemplate = this
    Printer(dropNullKeys = true, preserveOrder = true, stringStyle = Printer.StringStyle.Plain).pretty(template.asJson)
  }
}

trait Resource {
  def Properties: Properties
}
case class FunctionResource(Properties: Properties) extends Resource
case class HttpResource(Properties: HttpProperties) extends Resource

trait Properties

trait FunctionProperties {
  val Timeout: Long
  val MemorySize: Int
  val Events: ListMap[String, FunctionHttpApiEvent]
}

case class FunctionImageProperties(
    Timeout: Long,
    MemorySize: Int,
    Events: ListMap[String, FunctionHttpApiEvent],
    ImageUri: String,
    PackageType: String = "Image"
) extends Properties
    with FunctionProperties

case class FunctionCodeProperties(
    Timeout: Long,
    MemorySize: Int,
    Events: ListMap[String, FunctionHttpApiEvent],
    Runtime: String,
    CodeUri: String,
    Handler: String
) extends Properties
    with FunctionProperties

case class HttpProperties(StageName: String) extends Properties

case class FunctionHttpApiEvent(Properties: FunctionHttpApiEventProperties)

case class FunctionHttpApiEventProperties(
    ApiId: String,
    Method: String,
    Path: String,
    TimeoutInMillis: Long,
    PayloadFormatVersion: String = "2.0"
)

case class Output(Description: String, Value: ListMap[String, String])
