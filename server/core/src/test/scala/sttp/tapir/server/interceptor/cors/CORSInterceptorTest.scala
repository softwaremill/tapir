package sttp.tapir.server.interceptor.cors

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CORSInterceptorTest extends AnyFlatSpec with Matchers {
  "A custom CORS interceptor" should "be created with a valid config" in {
    noException should be thrownBy CORSInterceptor.customOrThrow(CORSConfig.default)
  }

  it should "not be created with an invalid config" in {
    // given
    val invalidConfig = CORSConfig.default.allowCredentials.allowAllOrigins

    // when & then
    an[IllegalArgumentException] should be thrownBy CORSInterceptor.customOrThrow(invalidConfig)
  }
}
