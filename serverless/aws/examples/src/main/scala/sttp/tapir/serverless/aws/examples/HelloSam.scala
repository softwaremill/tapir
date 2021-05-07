package sttp.tapir.serverless.aws.examples

import io.circe.syntax._
import sttp.tapir.serverless.aws.sam.{AwsSamInterpreter, AwsSamOptions, AwsSamTemplateEncoders, Printer}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

object HelloSam extends App {
  val targetFile = "/Users/kubinio/Desktop/workspace/tapir/serverless/aws/examples/src/main/resources/template.yaml"

  implicit val samOptions: AwsSamOptions = AwsSamOptions("hello", "???")

  val samTemplate = new AwsSamInterpreter().apply(List(HelloHandler.helloEndpoint.endpoint))

  val yaml = Printer(dropNullKeys = true, preserveOrder = true, stringStyle = Printer.StringStyle.Plain)
    .pretty(samTemplate.asJson(AwsSamTemplateEncoders.encoderSamTemplate))

  println(yaml)

  Files.write(Paths.get(targetFile), yaml.getBytes(StandardCharsets.UTF_8))
}
