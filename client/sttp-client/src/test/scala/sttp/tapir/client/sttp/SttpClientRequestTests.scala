package sttp.tapir.client.sttp

import sttp.tapir._
import sttp.client3._
import sttp.tapir.generic.auto._
import sttp.model.{Header, HeaderNames, MediaType, Part}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Defaults.createTempFile
import sttp.tapir.tests.Basic.in_flag_query_out_string
import sttp.tapir.tests.data
import sttp.tapir.tests.data.FruitData

class SttpClientRequestTests extends AnyFunSuite with Matchers {
  test("content-type header shouldn't be duplicated when converting to a part") {
    // given
    val testEndpoint = endpoint.post.in(multipartBody[FruitData])
    val testFile = createTempFile()

    // when
    val sttpClientRequest = SttpClientInterpreter()
      .toRequest(testEndpoint, Some(uri"http://localhost"))
      .apply(data.FruitData(Part("image", testFile, contentType = Some(MediaType.ImageJpeg))))

    // then
    val part = sttpClientRequest.body.asInstanceOf[MultipartBody[Any]].parts.head
    part.headers.filter(_.is(HeaderNames.ContentType)) shouldBe List(Header.contentType(MediaType.ImageJpeg))
  }

  test("should properly encode a flag parameter") {
    def uri(flagValue: Option[Boolean]) = SttpClientInterpreter()
      .toRequest(in_flag_query_out_string, Some(uri"http://localhost"))
      .apply(flagValue)
      .uri

    uri(None).toString shouldBe "http://localhost"
    uri(Some(true)).toString shouldBe "http://localhost?flag"
    uri(Some(false)).toString shouldBe "http://localhost?flag=false"
  }
}
