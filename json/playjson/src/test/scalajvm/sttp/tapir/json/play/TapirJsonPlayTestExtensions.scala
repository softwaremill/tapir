package sttp.tapir.json.play

import java.util.Date

import com.fasterxml.jackson.core.JsonParseException
import sttp.tapir.DecodeResult
import sttp.tapir.DecodeResult.Error.JsonDecodeException
import sttp.tapir.DecodeResult.Value

trait TapirJsonPlayTestExtensions { self: TapirJsonPlayTests =>
  // JVM only because Play JSON provides no Reads[java.util.Date] on Scala.js
  it should "encode and decode using custom Date serializer" in {
    val d = new Date
    testEncodeDecode(d)
  }

  it should "Fail to encode a badly formatted date" in {
    val codec = TapirJsonPlayCodec.readsWritesCodec[Date]
    val encoded = "\"OOPS-10-10 11:20:49.029\""

    codec.decode(encoded) match {
      case _: DecodeResult.Failure =>
        succeed
      case Value(d) =>
        fail(s"Should not have been able to decode this date: $d")
    }
  }

  // JVM only because com.fasterxml.jackson.core.JsonParseException is not available in Scala.js
  it should "return a JSON specific error on invalid JSON document decode failure" in {
    val input = """
                  |name: Alita
                  |yearOfBirth: 1985
                  |""".stripMargin

    val actual = customerCodec.decode(input)
    actual shouldBe a[DecodeResult.Error]

    val failure = actual.asInstanceOf[DecodeResult.Error]
    failure.original shouldEqual input
    failure.error shouldBe a[JsonDecodeException]

    val error = failure.error.asInstanceOf[JsonDecodeException]
    error.errors should contain theSameElementsAs List.empty
    error.underlying shouldBe a[JsonParseException]
  }
}
