package sttp.tapir.serverless.aws.lambda.runtime

private[runtime] object PlatformCompat {
  // Compiles, but would not link, scalalogging is not cross-compiled for ScalaJS
  type StrictLogging = com.typesafe.scalalogging.StrictLogging
}
