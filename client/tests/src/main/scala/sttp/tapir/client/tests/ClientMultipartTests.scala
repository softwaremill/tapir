package sttp.tapir.client.tests

import cats.effect.unsafe.implicits.global
import sttp.model.{MediaType, Part}
import sttp.tapir.tests.Multipart.{in_raw_multipart_out_string, in_simple_multipart_out_raw_string, in_simple_multipart_out_string}
import sttp.tapir.tests.data.{FruitAmount, FruitAmountWrapper}

trait ClientMultipartTests { this: ClientTests[Any] =>

  def multipartTests(): Unit = {
    testClient(in_simple_multipart_out_string, (), FruitAmount("melon", 10), Right("melon=10"))

    test(in_simple_multipart_out_raw_string.showDetail) {
      send(in_simple_multipart_out_raw_string, port, (), FruitAmountWrapper(FruitAmount("apple", 10), "Now!"))
        .unsafeToFuture()
        .map(_.toOption.get)
        .map { result =>
          val indexOfJson = result.indexOf("{\"fruit")
          val beforeJson = result.substring(0, indexOfJson)
          val afterJson = result.substring(indexOfJson)

          beforeJson should include("""Content-Disposition: form-data; name="fruitAmount"""")
          beforeJson should include("Content-Type: application/json")
          beforeJson should not include ("Content-Type: text/plain")

          afterJson should include("""Content-Disposition: form-data; name="notes"""")
          // We can't control the charset in Scala.js because dom.FormData sets the content-type in this case
          if (platformIsScalaJS)
            afterJson should include("Content-Type: text/plain")
          else
            afterJson should include("Content-Type: text/plain; charset=UTF-8")
          afterJson should not include ("Content-Type: application/json")
        }
    }

    testClient(
      in_raw_multipart_out_string,
      (),
      Seq(
        Part("operations", "{}".getBytes, contentType = Some(MediaType.ApplicationJson)),
        Part("map", """{ "0": ["variables.files.0"], "1":  ["variables.files.1"]}""".getBytes),
        Part("0", """image""".getBytes, contentType = Some(MediaType.ImagePng)).fileName("a.png"),
        Part("1", """text""".getBytes, contentType = Some(MediaType.TextPlain)).fileName("a.txt")
      ),
      Right("=")
    )

  }

}
