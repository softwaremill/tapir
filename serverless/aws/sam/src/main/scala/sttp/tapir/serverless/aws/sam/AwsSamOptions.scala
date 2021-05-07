package sttp.tapir.serverless.aws.sam

import scala.concurrent.duration.{DurationInt, FiniteDuration}

case class AwsSamOptions(
    namePrefix: String,
    imageUri: String,
    timeout: FiniteDuration = 10.seconds,
    memorySize: Int = 256
)
