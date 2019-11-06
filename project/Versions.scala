object Versions {
  type Version = Option[(Long, Long)] => String

  val http4s: Version = {
    case Some((2, 13)) => "0.21.0-M5"
    case _             => "0.21.0-M5"
  }
  val cats = "2.0.0"
  val circe = "0.12.3"
  val circeYaml = "0.11.0-M1"
  val sttp = "2.0.0-M11"
  val akkaHttp = "10.1.10"
  val akkaStreams = "2.5.26"
  val swaggerUi = "3.24.0"
  val upickle = "0.8.0"
  val playJson = "2.7.4"
  val silencer = "1.4.4"
  val finatra = "19.4.0"
}
