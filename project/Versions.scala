object Versions {

  type Version = Option[(Long, Long)] => String

  val http4s: Version = {
    case Some((2, 13)) => "0.21.0-M5"
    case _             => "0.20.10"
  }
  val sttp: Version = {
    case Some((2, 13)) => "1.6.7"
    case _             => "1.6.6"
  }
  val circe = "0.12.1"
  val circeYaml = "0.11.0-M1"
  val akkaHttp = "10.1.10"
  val akkaStreams = "2.5.25"
  val swaggerUi = "3.23.8"
  val upickle = "0.8.0"
  val playJson = "2.7.4"
  val silencer = "1.4.4"
}
