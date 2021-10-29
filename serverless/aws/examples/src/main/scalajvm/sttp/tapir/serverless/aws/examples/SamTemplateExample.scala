package sttp.tapir.serverless.aws.examples

import sttp.tapir.serverless.aws.examples.LambdaApiExample.helloEndpoint
import sttp.tapir.serverless.aws.sam.{AwsSamInterpreter, AwsSamOptions, CodeSource}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}

/** Before running the actual example we need to interpret our api as SAM template */
object SamTemplateExample extends App {

  val jarPath = Paths.get("serverless/aws/examples/target/jvm-2.13/tapir-aws-examples.jar").toAbsolutePath.toString

  val samOptions: AwsSamOptions = AwsSamOptions(
    "PersonsApi",
    source =
      /** Specifying a fat jar build from example sources */
      CodeSource(
        runtime = "java11",
        codeUri = jarPath,
        handler = "sttp.tapir.serverless.aws.examples.LambdaApiExample::handleRequest"
      )
  )

  val templateYaml = AwsSamInterpreter(samOptions).toSamTemplate(helloEndpoint).toYaml

  /** Write template to file, it's required to run the example using sam local */
  Files.write(Paths.get("template.yaml"), templateYaml.getBytes(UTF_8))

}
