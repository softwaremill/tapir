package sttp.tapir.server.interceptor.cors

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CORSConfigTest extends AnyFlatSpec with Matchers {

  behavior of "CORS config"

  it should "provide a valid default config" in {
    CORSConfig.default.isValid shouldBe true
  }

  it should "detect an invalid config" in {
    // given
    val withAllowedCredentials = CORSConfig.default.allowCredentials
    val invalidConfigs = List(
      withAllowedCredentials.allowAllOrigins,
      withAllowedCredentials.allowAllOrigins.allowAllHeaders,
      withAllowedCredentials.allowAllOrigins.allowAllMethods,
      withAllowedCredentials.allowAllOrigins.allowAllHeaders.allowAllMethods,
      withAllowedCredentials.allowAllHeaders,
      withAllowedCredentials.allowAllHeaders.allowAllMethods,
      withAllowedCredentials.allowAllMethods
    )

    // when
    val results = invalidConfigs.map(_.isValid)

    // then
    results should contain only false
  }
}
