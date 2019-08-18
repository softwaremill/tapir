package tapir.json.upickle

import upickle.default._
import org.scalatest.{FlatSpec, Matchers}
import java.util.Date
import tapir._
import tapir.DecodeResult._

object TapirJsonuPickleCodec extends TapirJsonuPickle

class TapirJsonuPickleTests extends FlatSpec with Matchers {

  case class Customer(name: String, yearOfBirth: Int, lastPurchase: Option[Long])

  object Customer {
    implicit val rw: ReadWriter[Customer] = macroRW
  }

  val customerDecoder = TapirJsonuPickleCodec.encoderDecoderCodec[Customer]

  it should "encode and decode Scala case class with non-empty Option elements" in {

    val customer = Customer("Alita", 1985, Some(1566150331L))
    val encoded = customerDecoder.encode(customer)

    customerDecoder.decode(encoded).map { decoded =>
      decoded shouldBe customer
    }
  }

  it should "encode and decode Scala case class with empty Option elements" in {

    val customer = Customer("Alita", 1985, None)
    val encoded = customerDecoder.encode(customer)

    customerDecoder.decode(encoded).map { decoded =>
      decoded shouldBe customer
    }
  }

  it should "encode and decode String type" in {

    val codec = TapirJsonuPickleCodec.encoderDecoderCodec[String]

    val s = "Hello world!"
    val encoded = codec.encode(s)
    codec.decode(encoded).map { decoded =>
      decoded shouldBe s
    }
  }

  it should "encode and decode Long type" in {

    val codec = TapirJsonuPickleCodec.encoderDecoderCodec[Long]

    val l = 1566150331L
    val encoded = codec.encode(l)
    codec.decode(encoded).map { decoded =>
      decoded shouldBe l
    }
  }

  // Custom Date serialization

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

    val codec = TapirJsonuPickleCodec.encoderDecoderCodec[Date]
    val d = new Date
    val encoded = codec.encode(d)

    codec.decode(encoded) match {
      case f: DecodeFailure =>
        fail(f.toString)
      case Value(decoded) =>
        decoded.compareTo(d) shouldBe 0
    }
  }

  it should "Fail to encode a badly formatted date" in {

    import DateConversionUtil._

    val codec = TapirJsonuPickleCodec.encoderDecoderCodec[Date]
    val encoded = "\"OOPS-10-10 11:20:49.029\""

    codec.decode(encoded) match {
      case _: DecodeFailure =>
        succeed
      case Value(d) =>
        fail(s"Should not have been able to decode this date: $d")
    }
  }
}