package sttp.tapir.serverless.aws.sam

import sttp.model.Method
import sttp.model.headers.Origin
import sttp.tapir.serverless.aws.sam.HttpApiProperties._
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}

case class AwsSamOptions(
    namePrefix: String,
    source: FunctionSource,
    timeout: FiniteDuration = 20.seconds,
    memorySize: Int = 512,
    httpApi: Option[HttpApiProperties] = None
)

sealed trait FunctionSource
case class ImageSource(imageUri: String) extends FunctionSource
case class CodeSource(runtime: String, codeUri: String, handler: String, environment: Map[String, String] = Map.empty)
    extends FunctionSource

case class HttpApiProperties(cors: Option[Cors])
object HttpApiProperties {
  case class Cors(
      allowCredentials: Option[AllowedCredentials],
      allowedHeaders: Option[AllowedHeaders],
      allowedMethods: Option[AllowedMethods],
      allowedOrigins: Option[AllowedOrigins],
      exposeHeaders: Option[ExposedHeaders],
      maxAge: Option[MaxAge]
  )

  sealed trait AllowedCredentials
  object AllowedCredentials {
    case object Allow extends AllowedCredentials
    case object Deny extends AllowedCredentials
  }

  sealed trait AllowedHeaders
  object AllowedHeaders {
    case object All extends AllowedHeaders
    case class Some(headersNames: Set[String]) extends AllowedHeaders
  }

  sealed trait AllowedMethods
  object AllowedMethods {
    case object All extends AllowedMethods
    case class Some(methods: Set[Method]) extends AllowedMethods
  }

  sealed trait AllowedOrigins
  object AllowedOrigin {
    case object All extends AllowedOrigins
    case class Some(origins: Set[Origin]) extends AllowedOrigins
  }

  sealed trait ExposedHeaders
  object ExposedHeaders {
    case object All extends ExposedHeaders
    case class Some(headerNames: Set[String]) extends ExposedHeaders
  }

  sealed trait MaxAge
  object MaxAge {
    case class Some(duration: Duration) extends MaxAge
  }
}
