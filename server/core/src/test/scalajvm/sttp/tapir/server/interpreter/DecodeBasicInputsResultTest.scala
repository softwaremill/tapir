package sttp.tapir.server.interpreter

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.Uri._
import sttp.model._
import sttp.tapir._
import sttp.tapir.model._

import scala.collection.immutable

// TODO: move to shared sources after https://github.com/softwaremill/sttp-model/issues/188 is fixed
class DecodeBasicInputsResultTest extends AnyFlatSpec with Matchers {

  def testRequest(testHeader: Header): ServerRequest = {
    new ServerRequest {
      override def protocol: String = ""
      override def connectionInfo: ConnectionInfo = ConnectionInfo(None, None, None)
      override def underlying: Any = ()
      override def pathSegments: List[String] = Nil
      override def queryParameters: QueryParams = QueryParams.fromSeq(Nil)
      override def method: Method = Method.GET
      override def uri: Uri = uri"http://example.com"
      override def headers: immutable.Seq[Header] = List(testHeader)
      override def attribute[T](k: AttributeKey[T]): Option[T] = None
      override def attribute[T](k: AttributeKey[T], v: T): ServerRequest = this
      override def withUnderlying(underlying: Any): ServerRequest = this
    }
  }

  val testData = List(
    ("multipart/form-data", "multipart/form-data", true),
    ("multipart/form-data", "multipart/form-data; boundary=xyz", true),
    ("text/plain", "text/plain; charset=utf-8", false),
    ("text/plain; charset=utf-8", "text/plain; charset=utf-8", true)
  )

  for ((endpointContentType, inputContentType, expectDecodeSuccess) <- testData) {
    val expectedStatus = if (expectDecodeSuccess) "succeed" else "fail"
    s"decoding endpoint with fixed content-type $endpointContentType and input content-type $inputContentType" should expectedStatus in {
      val testEp = endpoint.get.in(header(Header(HeaderNames.ContentType, endpointContentType)))
      val request = testRequest(Header(HeaderNames.ContentType, inputContentType))
      val (decodeResult, _) = DecodeBasicInputs.apply(testEp.input, DecodeInputsContext(request))

      if (expectDecodeSuccess)
        decodeResult shouldBe a[DecodeBasicInputsResult.Values]
      else
        decodeResult shouldBe a[DecodeBasicInputsResult.Failure]
    }
  }
}
