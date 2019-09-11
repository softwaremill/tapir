object Versions {

  type Version = Option[(Long, Long)] => String

  val http4s: Version = {
    case Some((2, 13)) => "0.21.0-M4"
    case _             => "0.20.10"
  }
  val circe = "0.12.1"
  val circeYaml = "0.11.0-M1"
  val sttp = "1.6.6"
  val akkaHttp = "10.1.9"
  val akkaStreams = "2.5.25"
  val swaggerUi = "3.23.8"
  val upickle = "0.7.5"
  val playJson = "2.7.4"
}
