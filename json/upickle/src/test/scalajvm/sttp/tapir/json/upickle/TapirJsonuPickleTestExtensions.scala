package sttp.tapir.json.upickle

import upickle.default._
import java.util.Date

import sttp.tapir._
import sttp.tapir.DecodeResult._

trait TapirJsonuPickleTestExtensions { self: TapirJsonuPickleTests =>

  // Custom Date serialization

  // JVM only because java.text.SimpleDateFormat is not available on Scala.js
  object DateConversionUtil {
    val dateFormatString = "yyyy-MM-dd HH:mm:ss.SSS"

    implicit val rw1 = upickle.default
      .readwriter[String]
      .bimap[Date](
        date => {
          val sdf = new java.text.SimpleDateFormat(dateFormatString)
          sdf.format(date)
        },
        s => {
          val dateFormat = new java.text.SimpleDateFormat(dateFormatString)
          dateFormat.parse(s)
        }
      )
  }

  it should "encode and decode using custom Date serializer" in {
    import DateConversionUtil._
    val d = new Date
    testEncodeDecode(d)
  }

  it should "Fail to encode a badly formatted date" in {
    import DateConversionUtil._

    val codec = TapirJsonuPickleCodec.readWriterCodec[Date]
    val encoded = "\"OOPS-10-10 11:20:49.029\""

    codec.decode(encoded) match {
      case _: DecodeResult.Failure =>
        succeed
      case Value(d) =>
        fail(s"Should not have been able to decode this date: $d")
    }
  }
}
