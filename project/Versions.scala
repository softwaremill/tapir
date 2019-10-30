object Versions {

  type Version = Option[(Long, Long)] => String

  val http4s: Version = {
    case Some((2, 11)) => "0.20.10"
    case _             => "0.21.0-M5"
  }
  val sttp: Version = {
    case Some((2, 11)) => "1.6.6"
    case _             => "1.6.7"
  }
  val cats = "2.0.0"
  val circe = "0.12.2"
  val circeYaml = "0.11.0-M1"
  val akkaHttp = "10.1.10"
  val akkaStreams = "2.5.26"
  val swaggerUi = "3.23.11"
  val upickle = "0.8.0"
  val playJson = "2.7.4"
  val silencer = "1.4.4"
  val finatra = "19.4.0"
}
