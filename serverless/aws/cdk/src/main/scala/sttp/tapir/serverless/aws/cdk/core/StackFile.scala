package sttp.tapir.serverless.aws.cdk.core

case class StackFile(
    apiName: String,
    lambdaName: String,
    runtime: String,
    jarPath: String,
    handler: String,
    timeout: Long,
    memorySize: Int
) {
  // productElementNames does not work with Scala 2.12
  def getFields: List[String] = List(
    "apiName",
    "lambdaName",
    "runtime",
    "jarPath",
    "handler",
    "timeout",
    "memorySize"
  )

  // productElementNames does not work with Scala 2.12
  def getValue(field: String): String = field match {
    case "apiName"    => apiName
    case "lambdaName" => lambdaName
    case "runtime"    => runtime
    case "jarPath"    => jarPath
    case "handler"    => handler
    case "timeout"    => timeout.toString
    case "memorySize" => memorySize.toString
    case _            => ""
  }
}
