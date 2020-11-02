package sttp.tapir.client.tests

import sttp.tapir.tests._

trait ClientMultipartTests { this: ClientTests[Any] =>

  def multipartTests(): Unit = {
    testClient(in_simple_multipart_out_string, FruitAmount("melon", 10), Right("melon=10"))

    test(in_simple_multipart_out_raw_string.showDetail) {
      val result = send(in_simple_multipart_out_raw_string, port, FruitAmountWrapper(FruitAmount("apple", 10), "Now!"))
        .unsafeRunSync()
        .right
        .get

      val indexOfJson = result.indexOf("{\"fruit")
      val beforeJson = result.substring(0, indexOfJson)
      val afterJson = result.substring(indexOfJson)

      beforeJson should include("""Content-Disposition: form-data; name="fruitAmount"""")
      beforeJson should include("Content-Type: application/json")
      beforeJson should not include ("Content-Type: text/plain")

      afterJson should include("""Content-Disposition: form-data; name="notes"""")
      afterJson should include("Content-Type: text/plain; charset=UTF-8")
      afterJson should not include ("Content-Type: application/json")
    }
  }

}
