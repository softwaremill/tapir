package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SecondaryBodyTest extends AnyFlatSpec with Matchers {
  it should "mark a body as secondary" in {
    stringBody.asSecondary.isSecondary shouldBe true
  }

  it should "leave a plain body unmarked" in {
    stringBody.isSecondary shouldBe false
  }

  it should "not treat a key built from the same name as a secondary marker" in {
    fileBody.attribute(new AttributeKey[Unit]("sttp.tapir.internal.SecondaryBody"), ()).isSecondary shouldBe false
  }

  it should "not compile for file bodies" in {
    assertDoesNotCompile("fileBody.asSecondary")
  }

  it should "not compile for multipart bodies" in {
    assertDoesNotCompile("multipartBody.asSecondary")
  }

  it should "not compile for oneOfBody" in {
    // OneOfBody has no asSecondary member at all, so this doesn't exercise the ReplayableRawBody constraint
    assertDoesNotCompile("""oneOfBody(stringBody, stringBody).asSecondary""")
  }

  it should "render a secondary body distinctly in show" in {
    stringBody.asSecondary.show shouldBe "{secondary body as text/plain (UTF-8)}"
  }

  it should "render a plain body unchanged in show" in {
    stringBody.show shouldBe "{body as text/plain (UTF-8)}"
  }
}
