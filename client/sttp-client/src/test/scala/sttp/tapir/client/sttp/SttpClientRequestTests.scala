package sttp.tapir.client.sttp

import java.io.File

import sttp.tapir._
import sttp.client._
import org.scalatest.{FunSuite, Matchers}
import sttp.model.{Header, HeaderNames, MediaType, Part}
import sttp.tapir.tests.FruitData

class SttpClientRequestTests extends FunSuite with Matchers {
  test("content-type header shouldn't be duplicated when converting to a part") {
    // given
    val testEndpoint = endpoint.post.in(multipartBody[FruitData])
    val testFile = File.createTempFile("tapir-", "image")

    // when
    val sttpClientRequest = testEndpoint
      .toSttpRequest(uri"http://localhost")
      .apply(FruitData(Part("image", testFile, contentType = Some(MediaType.ImageJpeg))))

    // then
    val part = sttpClientRequest.body.asInstanceOf[MultipartBody].parts.head
    part.headers.filter(_.is(HeaderNames.ContentType)) shouldBe List(Header.contentType(MediaType.ImageJpeg))
  }
}
