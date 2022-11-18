package sttp.tapir.serverless.aws.sam

import sttp.tapir.serverless.aws.sam.HttpApiProperties._
import scala.concurrent.duration.{DurationInt, FiniteDuration}

case class AwsSamOptions(
    namePrefix: String,
    httpApi: Option[HttpApiProperties] = None,
    source: FunctionSource,
    timeout: FiniteDuration = 10.seconds,
    memorySize: Int = 256
)

sealed trait FunctionSource
case class ImageSource(imageUri: String) extends FunctionSource
case class CodeSource(runtime: String, codeUri: String, handler: String) extends FunctionSource

case class HttpApiProperties(cors: Option[Cors])
object HttpApiProperties {
  case class Cors(
      allowCredentials: Option[Boolean],
      allowedHeaders: List[String],
      allowedMethods: List[String],
      allowedOrigins: List[String],
      exposeHeaders: List[String],
      maxAge: Option[Long]
  )
}
