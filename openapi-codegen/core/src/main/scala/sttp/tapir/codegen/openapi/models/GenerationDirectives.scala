package sttp.tapir.codegen.openapi.models

object GenerationDirectives {
  val extensionKey = "tapir-codegen-directives"
  val jsonBodyAsString = "json-body-as-string"
  val forceEager = "force-eager"
  val forceReqEager = "force-req-body-eager"
  val forceRespEager = "force-resp-body-eager"
  val forceStreaming = "force-streaming"
  val forceReqStreaming = "force-req-body-streaming"
  val forceRespStreaming = "force-resp-body-streaming"
  val securityPrefixKey = "tapir-codegen-security-path-prefixes"
}
