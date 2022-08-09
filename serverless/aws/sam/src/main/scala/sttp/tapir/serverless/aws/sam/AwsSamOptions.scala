package sttp.tapir.serverless.aws.sam

import scala.concurrent.duration.{DurationInt, FiniteDuration}

case class AwsSamOptions(
    namePrefix: String,
    source: FunctionSource,
    timeout: FiniteDuration = 20.seconds,
    memorySize: Int = 256
)

sealed trait FunctionSource
case class ImageSource(imageUri: String) extends FunctionSource
case class CodeSource(runtime: String, codeUri: String, handler: String) extends FunctionSource
