package sttp.tapir.serverless.aws.cdk

import scala.concurrent.duration._

case class AwsCdkOptions(
    codeUri: String,
    handler: String,
    apiName: String = "API",
    lambdaName: String = "TapirHandler",
    templateFilePath: String = "/app-template/lib/stack-template.ts",
    timeout: FiniteDuration = 20.seconds,
    memorySizeInMB: Int = 2048,
    runtime: AwsCdkOptions.Runtime = AwsCdkOptions.Runtime.Java11,
    outputDir: String = "cdk"
)

object AwsCdkOptions {
  sealed trait Runtime

  object Runtime {
    case object Java8 extends Runtime
    case object Java8Corretto extends Runtime
    case object Java11 extends Runtime
  }
}
