package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.headers.ETag

class FilesSpec extends AnyFlatSpec with Matchers {

  "Default Etag" should "be calulated with lastModified and length and RangeValue" in {
    files.defaultETag(lastModified = 1682079650000L, length = 3489500L, range = None) shouldBe ETag("187a3c290d0-353edc", weak = false)
    files.defaultETag(lastModified = 769523317000L, length = 2L, range = None) shouldBe ETag("b32b29f908-2", weak = false)
    files.defaultETag(lastModified = 769523317000L, length = 2L, range = Some(RangeValue(start = None, end = None, fileSize = 2L))) shouldBe ETag("b32b29f908-2", weak = false)
    files.defaultETag(lastModified = 769523317000L, length = 2000L, range = Some(RangeValue(start = Some(100L), end = None, fileSize = 2000L))) shouldBe ETag("b32b29f908-7d0-64-7d0", weak = false)
    files.defaultETag(lastModified = 769590203000L, length = 10485760L, range = Some(RangeValue(start = None, end = Some(3178474L), fileSize = 3178474L))) shouldBe ETag("b32f269278-a00000-0-307fea", weak = false)
  }
}
