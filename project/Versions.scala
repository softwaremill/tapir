object Versions {

  type Version = Option[(Long, Long)] => String

  val http4s: Version = {
    case Some((2, 13)) => "0.21.0-M2"
    case _             => "0.20.6"
  }
  val circe: Version = {
    case Some((2, 13)) => "0.12.0-M4"
    case _             => "0.11.1"
  }
  val circeYaml: Version = {
    case Some((2, 13)) => "0.11.0-M1"
    case _             => "0.10.0"
  }
  val sttp = "1.6.4"
  val akkaHttp = "10.1.9"
  val akkaStreams = "2.5.23"
  val swaggerUi = "3.23.2"
  val upickle = "0.7.5"
}
