package sttp.tapir.serverless.aws.cdk.core

import cats.data.NonEmptyList
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ResourceTest extends AnyFunSuite with Matchers {

  test("dependent resources") {
    val tree = List(
      Node(
        Fixed("hello").get,
        List(GET),
        List(
          Node(
            Parameter("name").get,
            List(GET)
          )
        )
      )
    )

    val expected = List(
      Resource("hello", "hello", NonEmptyList.one(GET), ""),
      Resource("helloName", "{name}", NonEmptyList.one(GET), "hello")
    )

    val resources = Resource.generate(tree)
    assert(expected.equals(resources))
  }

  test("nested endpoint") {
    val tree = List(
      Node(
        Fixed("hello").get,
        List.empty,
        List(
          Node(
            Parameter("name").get,
            List(GET)
          )
        )
      )
    )

    val expected = List(
      Resource("helloName", "hello/{name}", NonEmptyList.one(GET), "")
    )

    val resources = Resource.generate(tree)
    assert(expected.equals(resources))
  }

  test("mixed") {
    val tree =
      List(
        Node(
          Fixed("country").get,
          List.empty,
          List(
            Node(
              Parameter("countryId").get,
              List.empty,
              List(
                Node(
                  Fixed("province").get,
                  List.empty,
                  List(
                    Node(
                      Parameter("provinceId").get,
                      List(GET),
                      List(
                        Node(
                          Fixed("governor").get,
                          List(GET)
                        )
                      )
                    )
                  )
                )
              )
            )
          )
        ),
        Node(
          Fixed("product").get,
          List.empty,
          List(
            Node(
              Parameter("id").get,
              List.empty,
              List(
                Node(
                  Fixed("tariff").get,
                  List(GET)
                )
              )
            )
          )
        )
      )

    val expected = List(
      Resource("countryCountryIdProvinceProvinceId", "country/{countryId}/province/{provinceId}", NonEmptyList.one(GET), ""),
      Resource("countryCountryIdProvinceProvinceIdGovernor", "governor", NonEmptyList.one(GET), "countryCountryIdProvinceProvinceId"),
      Resource("productIdTariff", "product/{id}/tariff", NonEmptyList.one(GET), "")
    )

    val resources = Resource.generate(tree)
    assert(expected.equals(resources))
  }

  test("nested endpoints with gaps") {
    val tree =
      List(
        Node(
          Fixed("a").get,
          List.empty,
          List(
            Node(
              Fixed("b").get,
              List(GET),
              List(
                Node(
                  Fixed("c").get,
                  List.empty,
                  List(
                    Node(
                      Fixed("d").get,
                      List(GET)
                    )
                  )
                )
              )
            )
          )
        )
      )

    val expected = List(
      Resource("aB", "a/b", NonEmptyList.one(GET), ""),
      Resource("aBCD", "c/d", NonEmptyList.one(GET), "aB")
    )

    val resources = Resource.generate(tree)
    assert(expected.equals(resources))
  }

  test("parameter and fixed segment with the same name") {
    val tree = List(
      Node(
        Fixed("hello").get,
        List(GET)
      ),
      Node(
        Parameter("hello").get,
        List(GET)
      )
    )

    val expected = List(
      Resource("hello", "hello", NonEmptyList.one(GET), ""),
      Resource("hello_1", "{hello}", NonEmptyList.one(GET), "")
    )

    val resources = Resource.generate(tree)
    assert(expected.equals(resources))
  }

  test("numeric segment") {
    val tree = List(
      Node(
        Fixed("1").get,
        List(GET)
      )
    )

    val expected = List(
      Resource("p1", "1", NonEmptyList.one(GET), "") // expect prefix
    )

    val resources = Resource.generate(tree)
    assert(expected.equals(resources))
  }

  test("nested numeric segments that leads to non unique names") {
    val tree = List(
      Node(
        Fixed("1").get,
        List(GET),
        List(
          Node(
            Fixed("1").get,
            List(GET)
          )
        )
      ),
      Node(
        Fixed("11").get,
        List(GET)
      )
    )

    val expected = List(
      Resource("p1", "1", NonEmptyList.one(GET), ""),
      Resource("p11", "1", NonEmptyList.one(GET), "p1"),
      Resource("p11_1", "11", NonEmptyList.one(GET), "")
    )

    val resources = Resource.generate(tree)
    assert(expected.equals(resources))
  }

  test("increasing suffix by more than one") {
    val tree = List(
      Node(
        Fixed("12").get,
        List.empty,
        List(
          Node(
            Fixed("3").get,
            List(GET)
          )
        )
      ),
      Node(
        Fixed("123").get,
        List(GET)
      ),
      Node(
        Fixed("1").get,
        List.empty,
        List(
          Node(
            Fixed("23").get,
            List(GET)
          )
        )
      ),
      Node(
        Fixed("1").get,
        List.empty,
        List(
          Node(
            Fixed("2").get,
            List.empty,
            List(
              Node(
                Fixed("3").get,
                List(GET)
              )
            )
          )
        )
      )
    )

    val expected = List(
      Resource("p123", "12/3", NonEmptyList.one(GET), ""),
      Resource("p123_1", "123", NonEmptyList.one(GET), ""),
      Resource("p123_2", "1/23", NonEmptyList.one(GET), ""),
      Resource("p123_3", "1/2/3", NonEmptyList.one(GET), "")
    )

    val resources = Resource.generate(tree)
    assert(expected.equals(resources))
  }

  test("encoded segment") {
    val tree = List(
      Node(
        Fixed("hello").get,
        List(GET),
        List(
          Node(
            Fixed("bob%3F").get,
            List(GET)
          )
        )
      )
    )

    val expected = List(
      Resource("hello", "hello", NonEmptyList.one(GET), ""),
      Resource("helloBob", "bob%3F", NonEmptyList.one(GET), "hello")
    )

    val resources = Resource.generate(tree)
    assert(expected.equals(resources))
  }

  test("stripped repeated") {
    val tree = List(
      Node(
        Fixed("hello").get,
        List(GET)
      ),
      Node(
        Fixed("hello!").get,
        List(GET)
      ),
      Node(
        Fixed("hello?").get,
        List(GET)
      )
    )

    val expected = List(
      Resource("hello", "hello", NonEmptyList.one(GET), ""),
      Resource("hello_1", "hello!", NonEmptyList.one(GET), ""),
      Resource("hello_2", "hello?", NonEmptyList.one(GET), "")
    )

    val resources = Resource.generate(tree)
    assert(expected.equals(resources))
  }

  //todo: test non suppoerted method

  test("many methods") {
    val tree = List(
      Node(
        Fixed("hello").get,
        List(GET, POST),
      )
    )

    val expected = List(
      Resource("hello", "hello", NonEmptyList.of(GET, POST), ""),
    )

    val resources = Resource.generate(tree)
    assert(expected.equals(resources))
  }

  test("many methods with different order") {
    val tree = List(
      Node(
        Fixed("hello").get,
        List(POST, GET),
      )
    )

    val expected = List(
      Resource("hello", "hello", NonEmptyList.of(POST, GET), ""),
    )

    val resources = Resource.generate(tree)
    assert(expected.equals(resources))
  }

  test("duplicated methods") {
    val tree = List(
      Node(
        Fixed("hello").get,
        List(POST, POST),
      )
    )

    val expected = List(
      Resource("hello", "hello", NonEmptyList.of(POST), ""),
    )

    val resources = Resource.generate(tree)
    assert(expected.equals(resources))
  }

  // todo: check if skipped parts does not add to variables registry
}
