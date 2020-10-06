package sttp.tapir.json.play

import java.util.Date

import sttp.tapir.DecodeResult
import sttp.tapir.DecodeResult.Value

class TapirJsonPlayTestsJVM extends TapirJsonPlayTests {
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
}
