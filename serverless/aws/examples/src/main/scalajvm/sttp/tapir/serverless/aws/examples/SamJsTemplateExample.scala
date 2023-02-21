package sttp.tapir.serverless.aws.examples

import sttp.tapir.serverless.aws.examples.LambdaApiExample.helloEndpoint
import sttp.tapir.serverless.aws.sam.{AwsSamInterpreter, AwsSamOptions, CodeSource}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}

/** Before running the actual example we need to interpret our api as SAM template */
object SamJsTemplateExample extends App {

  val jsPath = Paths.get("serverless/aws/examples/target/js-2.13/tapir-aws-examples-fastopt").toAbsolutePath.toString

  val samOptions: AwsSamOptions = AwsSamOptions(
    "PersonsApi",
    source =
      /** Specifying a Node.js module build from example sources */
      CodeSource(
        runtime = "nodejs14.x",
        codeUri = jsPath,
        handler = "main.LambdaApiJsResourceExampleHandler"
      )
  )

  val templateYaml = AwsSamInterpreter(samOptions).toSamTemplate(helloEndpoint).toYaml

  /** Write template to file, it's required to run the example using sam local */
  Files.write(Paths.get("template.yaml"), templateYaml.getBytes(UTF_8))

}
