package sttp.tapir.server.tests

import cats.implicits._
import org.scalatest.matchers.should.Matchers._
import sttp.client4._
import sttp.model.HeaderNames.Accept
import sttp.model.MediaType._
import sttp.model.StatusCode
import sttp.monad.MonadError
import sttp.tapir.tests.OneOfBody.{
  in_one_of_json_text_range_out_string,
  in_one_of_json_xml_text_out_string,
  in_string_out_one_of_json_xml_text
}
import sttp.tapir.tests._
import sttp.tapir.tests.data.Fruit

class ServerOneOfBodyTests[F[_], OPTIONS, ROUTE](
    createServerTest: CreateServerTest[F, Any, OPTIONS, ROUTE]
)(implicit
    m: MonadError[F]
) {
  import createServerTest._

  def tests(): List[Test] = List(
    testServer(in_one_of_json_xml_text_out_string)((fruit: Fruit) => pureResult(fruit.f.asRight[Unit])) { (backend, baseUri) =>
      val post = basicRequest.post(uri"$baseUri").response(asStringAlways)
      post.body("""{"f":"apple"}""").contentType(ApplicationJson).send(backend).map(_.body shouldBe "apple") >>
        post.body("<f>orange</f>").contentType(ApplicationXml).send(backend).map(_.body shouldBe "orange") >>
        post.body("pear").contentType(TextPlain).send(backend).map(_.body shouldBe "pear") >>
        post.body("!*@#").contentType(ApplicationPdf).send(backend).map(_.code shouldBe StatusCode.UnsupportedMediaType)
    },
    testServer(in_one_of_json_text_range_out_string)((fruit: Fruit) => pureResult(fruit.f.asRight[Unit])) { (backend, baseUri) =>
      val post = basicRequest.post(uri"$baseUri").response(asStringAlways)
      post.body("""{"f":"apple"}""").contentType(ApplicationJson).send(backend).map(_.body shouldBe "apple") >>
        post.body("<f>orange</f>").contentType(TextHtml).send(backend).map(_.body shouldBe "<f>orange</f>") >>
        post.body("pear").contentType(TextPlain).send(backend).map(_.body shouldBe "pear") >>
        post.body("!*@#").contentType(ApplicationPdf).send(backend).map(_.code shouldBe StatusCode.UnsupportedMediaType)
    },
    testServer(in_string_out_one_of_json_xml_text)((fruit: String) => pureResult(Fruit(fruit).asRight[Unit])) { (backend, baseUri) =>
      val post = basicRequest.post(uri"$baseUri").response(asStringAlways)
      post.body("apple").header(Accept, ApplicationJson.toString()).send(backend).map(_.body shouldBe """{"f":"apple"}""") >>
        post.body("orange").header(Accept, ApplicationXml.toString()).send(backend).map(_.body shouldBe """<f>orange</f>""") >>
        post.body("pear").header(Accept, TextPlain.toString()).send(backend).map(_.body shouldBe "pear") >>
        post.body("apple").send(backend).map(_.body shouldBe """{"f":"apple"}""") >> // default
        post.body("apple").header(Accept, ApplicationPdf.toString()).send(backend).map(_.code shouldBe StatusCode.NotAcceptable)
    }
  )
}
