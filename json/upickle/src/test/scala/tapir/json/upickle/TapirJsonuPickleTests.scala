package tapir.json.upickle

import upickle.default._
import org.scalatest.{FlatSpec, Matchers, Assertion}
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

  // Helper to test encoding then decoding an object is the same as the original
  def testEncodeDecode[T: ReadWriter: SchemaFor](original: T): Assertion = {
    val codec = TapirJsonuPickleCodec.encoderDecoderCodec[T]

    val encoded = codec.encode(original)
    codec.decode(encoded) match {
      case Value(d) =>
        d shouldBe original
      case f: DecodeFailure =>
        fail(f.toString)
    }
  }

  it should "encode and decode Scala case class with non-empty Option elements" in {

    val customer = Customer("Alita", 1985, Some(1566150331L))
    testEncodeDecode(customer)
  }

  it should "encode and decode Scala case class with empty Option elements" in {

    val customer = Customer("Alita", 1985, None)
    testEncodeDecode(customer)
  }

  it should "encode and decode String type" in {
    testEncodeDecode("Hello, World!")
  }

  it should "encode and decode Long type" in {
    testEncodeDecode(1566150331L)
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
    val d = new Date
    testEncodeDecode(d)
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
