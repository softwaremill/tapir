package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}

class JsonDecodeExceptionTests extends AnyFlatSpecLike with Matchers {

  it should "print failed paths" in {
    val error = JsonDecodeException(
      List(
        JsonError("error.path.missing", List(FieldName("obj"), FieldName("customer"), FieldName("yearOfBirth"))),
        JsonError("error.path.missing", List(FieldName("obj"), FieldName("items[0]"), FieldName("price")))
      ),
      new Exception("JsResultException")
    )
    error.getMessage shouldEqual
      "error.path.missing at 'obj.customer.yearOfBirth', error.path.missing at 'obj.items[0].price'"
  }

  it should "print underlying exception message when there is no failed paths" in {
    val error = JsonDecodeException(errors = List.empty, new Exception("ParseException"))
    error.getMessage shouldEqual "ParseException"
  }

}
