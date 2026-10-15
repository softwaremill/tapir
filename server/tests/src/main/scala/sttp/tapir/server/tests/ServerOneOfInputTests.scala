package sttp.tapir.server.tests

import cats.implicits._
import org.scalatest.matchers.should.Matchers._
import sttp.client4._
import sttp.model.MediaType._
import sttp.model.StatusCode
import sttp.monad.MonadError
import sttp.tapir.tests.Test
import sttp.tapir.tests.OneOfInput.{
  in_json_or_png_out_string,
  in_optional_json_or_png_out_string,
  LinkToExistingImage,
  PngImage
}

class ServerOneOfInputTests[F[_], OPTIONS, ROUTE](
    createServerTest: CreateServerTest[F, Any, OPTIONS, ROUTE]
)(implicit
    m: MonadError[F]
) {
  import createServerTest._

  private val href = "https://cdn.example/a.png"
  private val pngBytes = Array[Byte](1, 2, 3)

  def tests(): List[Test] = List(
    testServer(in_json_or_png_out_string) {
      case LinkToExistingImage(link) => pureResult(link.asRight[Unit])
      case PngImage(bytes)           => pureResult(bytes.length.toString.asRight[Unit])
    } { (backend, baseUri) =>
      val post = basicRequest.post(uri"$baseUri/one-of-input").response(asStringAlways)
      post.body(s"""{"href":"$href"}""").contentType(ApplicationJson).send(backend).map(_.body shouldBe href) >>
        post.body(pngBytes).contentType(ImagePng).send(backend).map(_.body shouldBe pngBytes.length.toString) >>
        // no body-less variant: a zero-length body with a matching content type is still that body
        post.body(Array.emptyByteArray).contentType(ImagePng).send(backend).map(_.body shouldBe "0") >>
        post.body("!*@#").contentType(ApplicationPdf).send(backend).map(_.code shouldBe StatusCode.UnsupportedMediaType)
    },
    testServer(in_optional_json_or_png_out_string) {
      case None                               => pureResult("none".asRight[Unit])
      case Some(LinkToExistingImage(link))    => pureResult(link.asRight[Unit])
      case Some(PngImage(bytes))              => pureResult(bytes.length.toString.asRight[Unit])
    } { (backend, baseUri) =>
      val post = basicRequest.post(uri"$baseUri/one-of-input-optional").response(asStringAlways)
      post.send(backend).map(_.body shouldBe "none") >>
        post.body(s"""{"href":"$href"}""").contentType(ApplicationJson).send(backend).map(_.body shouldBe href) >>
        post.body(pngBytes).contentType(ImagePng).send(backend).map(_.body shouldBe pngBytes.length.toString) >>
        // a content type with a zero-length body is the empty variant when one exists
        post.body(Array.emptyByteArray).contentType(ApplicationJson).send(backend).map(_.body shouldBe "none") >>
        post.body(Array.emptyByteArray).contentType(ApplicationPdf).send(backend).map(_.body shouldBe "none") >>
        post.body("!*@#").contentType(ApplicationPdf).send(backend).map(_.code shouldBe StatusCode.UnsupportedMediaType)
    }
  )
}
