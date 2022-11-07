package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.model.{CommaSeparated, Delimited}

class CodecDelimitedTest extends AnyFlatSpec with Matchers {
  it should "encode and decode comma-separated values" in {
    val c = implicitly[Codec[String, CommaSeparated[Int], TextPlain]].map(_.values)(Delimited(_))

    c.encode(List(1, 10, 24)) shouldBe "1,10,24"
    c.decode("1") shouldBe DecodeResult.Value(List(1))
    c.decode("1,10") shouldBe DecodeResult.Value(List(1, 10))
    c.decode("1,10,24") shouldBe DecodeResult.Value(List(1, 10, 24))
    c.decode("1,10,24,x") should matchPattern { case DecodeResult.Error("x", _) => }
  }

  it should "encode and decode comma-separated values in a list" in {
    val c =
      implicitly[Codec[List[String], Option[CommaSeparated[Int]], TextPlain]].map(_.map(_.values).getOrElse(Nil))(l => Some(Delimited(l)))

    c.encode(List(1, 10, 24)) shouldBe List("1,10,24")
    c.decode(Nil) shouldBe DecodeResult.Value(Nil)
    c.decode(List("1")) shouldBe DecodeResult.Value(List(1))
    c.decode(List("1,10")) shouldBe DecodeResult.Value(List(1, 10))
    c.decode(List("1,10,24")) shouldBe DecodeResult.Value(List(1, 10, 24))
    c.decode(List("1,10,24,x")) should matchPattern { case DecodeResult.Error("x", _) => }
  }
}
