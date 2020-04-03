package sttp.tapir.json.upickle

import upickle.default._
import org.scalatest.{Assertion, FlatSpec, Matchers}
import java.util.Date

import sttp.tapir.Codec.JsonCodec
import sttp.tapir._
import sttp.tapir.DecodeResult._

object TapirJsonuPickleCodec extends TapirJsonuPickle

class TapirJsonuPickleTests extends FlatSpec with Matchers {
  case class Customer(name: String, yearOfBirth: Int, lastPurchase: Option[Long])

  object Customer {
    implicit val rw: ReadWriter[Customer] = macroRW
  }

  val customerDecoder: JsonCodec[Customer] = TapirJsonuPickleCodec.readWriterCodec[Customer]

  // Helper to test encoding then decoding an object is the same as the original
  def testEncodeDecode[T: ReadWriter: Schema: Validator](original: T): Assertion = {
    val codec = TapirJsonuPickleCodec.readWriterCodec[T]

    val encoded = codec.encode(original)
    codec.decode(encoded) match {
      case Value(d) =>
        d shouldBe original
      case f: DecodeResult.Failure =>
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
