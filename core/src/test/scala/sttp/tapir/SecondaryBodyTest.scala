package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SecondaryBodyTest extends AnyFlatSpec with Matchers {
  it should "not treat a key built from the same name as a secondary marker" in {
    fileBody.attribute(new AttributeKey[Unit]("sttp.tapir.internal.SecondaryBody"), ()).isSecondary shouldBe false
  }

  it should "not compile for file bodies" in {
    assertDoesNotCompile("fileBody.asSecondary")
  }

  it should "not compile for multipart bodies" in {
    assertDoesNotCompile("multipartBody.asSecondary")
  }

  it should "render a secondary body distinctly in show" in {
    stringBody.asSecondary.show shouldBe "{secondary body as text/plain (UTF-8)}"
  }
}
