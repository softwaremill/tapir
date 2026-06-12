package sttp.tapir.server.ziopentelemetry

object OtlpEndpoint {

  /** OTLP gRPC endpoint to export telemetry data to.
    *
    * It can be set via:
    *
    *   - environment variable provided as `envVar`
    *   - environment variable "OTEL_EXPORTER_OTLP_ENDPOINT"
    *   - defaults to "http://localhost:4317"
    *
    * See https://opentelemetry.io/docs/specs/otel/configuration/sdk-environment-variables/#otel_exporter_otlp_endpoint.
    *
    * @param envVar
    * @return
    */
  def apply(envVar: String): Option[String] =
    sys.env
      .get(envVar)
      .orElse(sys.env.get("OTEL_EXPORTER_OTLP_ENDPOINT"))
}
