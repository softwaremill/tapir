package sttp.tapir.serverless.aws.sam

import scala.concurrent.duration.{DurationInt, FiniteDuration}

case class AwsSamOptions(
    namePrefix: String,
    source: FunctionSource,
    timeout: FiniteDuration = 10.seconds,
    memorySize: Int = 256
)

trait FunctionSource
case class ImageSource(imageUri: String) extends FunctionSource
case class CodeSource(runtime: String, codeUri: String, handler: String) extends FunctionSource
