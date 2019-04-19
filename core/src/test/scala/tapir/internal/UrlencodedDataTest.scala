package tapir.internal

import java.nio.charset.StandardCharsets

import org.scalatest.{FlatSpec, Matchers}

class UrlencodedDataTest extends FlatSpec with Matchers {

  def test(desc: String, s: String, expected: Seq[(String, String)]): Unit = {
    it should desc in {
      val decoded = UrlencodedData.decode(s, StandardCharsets.UTF_8)
      decoded shouldBe expected

      val encoded = UrlencodedData.encode(decoded, StandardCharsets.UTF_8)
      encoded shouldBe s
    }
  }

  test("handle empty string", "", Nil)
  test("handle single parameter", "x=10", Seq(("x", "10")))
  test("handle two parameters", "x=10&y=20", Seq(("x", "10"), ("y", "20")))
  test(
    "handle complex data",
    "name=John+Smith&grass=%C5%BAd%C5%BAb%C5%82o&amount=30",
    Seq(("name", "John Smith"), ("grass", "źdźbło"), ("amount", "30"))
  )
}
