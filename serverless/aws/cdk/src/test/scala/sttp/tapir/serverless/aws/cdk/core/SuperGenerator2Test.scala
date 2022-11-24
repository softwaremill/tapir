package sttp.tapir.serverless.aws.cdk.core

import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.serverless.aws.cdk.core

class SuperGenerator2Test extends AnyFlatSpec with Matchers with OptionValues {
  it should "return empty list for fixed path with missing methods" in {
    // given
    val tree = List(
      Node(
        name = Fixed("api").value,
        methods = List.empty,
        children = List.empty
      )
    )

    // when
    val result = SuperGenerator.generateV2(tree)

    // then
    result should contain theSameElementsInOrderAs Seq(
      "const rootApi = api.root.addResource('api');"
    )
  }

  it should "generate root api TS for fixed path with one element" in {
    // given
    val tree = List(
      Node(
        name = Fixed("api").value,
        methods = List(core.Method.GET),
        children = List.empty
      )
    )

    // when
    val result = SuperGenerator.generateV2(tree)

    // then
    result should contain theSameElementsInOrderAs List(
      "// GET /api",
      "const rootApi = api.root.addResource('api');",
      "rootApi.addMethod('GET');",
    )
  }

  it should "generate TS for fixed path with two element" in {
    // given
    val tree = List(
      Node(
        name = Fixed("api").value,
        methods = List.empty,
        children = List(
          Node(
            name = Fixed("hello").value,
            methods = List(core.Method.GET)
          )
        )
      )
    )

    // when
    val result = SuperGenerator.generateV2(tree)

    // then
    result should contain theSameElementsInOrderAs List(
      "const rootApi = api.root.addResource('api');",
      "",
      "// GET /api/hello",
      "const rootApiHello = rootApi.addResource('hello');",
      "rootApiHello.addMethod('GET');",
    )
  }

  // you may ask why 3 ;)
  it should "generate TS for path for path with 3 segments" in {
    // given
    val tree = List(
      Node(
        name = Fixed("api").value,
        methods = List.empty,
        children = List(
          Node(
            name = Fixed("hello").value,
            methods = List(core.Method.GET),
            children = List(
              Node(
                name = Parameter("name").value,
                methods = List(core.Method.POST)
              )
            )
          )
        )
      )
    )

    // when
    val result = SuperGenerator.generateV2(tree)

    // then
    result should contain theSameElementsInOrderAs List(
      "const rootApi = api.root.addResource('api');",
      "",
      "// GET /api/hello",
      "const rootApiHello = rootApi.addResource('hello');",
      "rootApiHello.addMethod('GET');",
      "",
      "// POST /api/hello/{name}",
      "const rootApiHelloName = rootApiHello.addResource('name');",
      "rootApiHelloName.addMethod('POST');",
    )
  }

//  test("nested endpoint") {
//    val tree = List(
//      Node(
//        Fixed("hello").get,
//        List.empty,
//        List(
//          Node(
//            Parameter("name").get,
//            List(GET)
//          )
//        )
//      )
//    )
//
//    val expected = List(
//      Resource(VariableName("helloName"), "hello/{name}", NonEmptyList.one(GET), "")
//    )
//
//    val resources = Resource.generate(tree)
//    assert(expected.equals(resources))
//  }
//
//  test("mixed") {
//    val tree =
//      List(
//        Node(
//          Fixed("country").get,
//          List.empty,
//          List(
//            Node(
//              Parameter("countryId").get,
//              List.empty,
//              List(
//                Node(
//                  Fixed("province").get,
//                  List.empty,
//                  List(
//                    Node(
//                      Parameter("provinceId").get,
//                      List(GET),
//                      List(
//                        Node(
//                          Fixed("governor").get,
//                          List(GET)
//                        )
//                      )
//                    )
//                  )
//                )
//              )
//            )
//          )
//        ),
//        Node(
//          Fixed("product").get,
//          List.empty,
//          List(
//            Node(
//              Parameter("id").get,
//              List.empty,
//              List(
//                Node(
//                  Fixed("tariff").get,
//                  List(GET)
//                )
//              )
//            )
//          )
//        )
//      )
//
//    val expected = List(
//      Resource(VariableName("countryCountryIdProvinceProvinceId"), "country/{countryId}/province/{provinceId}", NonEmptyList.one(GET), ""),
//      Resource(
//        VariableName("countryCountryIdProvinceProvinceIdGovernor"),
//        "governor",
//        NonEmptyList.one(GET),
//        "countryCountryIdProvinceProvinceId"
//      ),
//      Resource(VariableName("productIdTariff"), "product/{id}/tariff", NonEmptyList.one(GET), "")
//    )
//
//    val resources = Resource.generate(tree)
//    assert(expected.equals(resources))
//  }
//
//  test("nested endpoints with gaps") {
//    val tree =
//      List(
//        Node(
//          Fixed("a").get,
//          List.empty,
//          List(
//            Node(
//              Fixed("b").get,
//              List(GET),
//              List(
//                Node(
//                  Fixed("c").get,
//                  List.empty,
//                  List(
//                    Node(
//                      Fixed("d").get,
//                      List(GET)
//                    )
//                  )
//                )
//              )
//            )
//          )
//        )
//      )
//
//    val expected = List(
//      Resource(VariableName("aB"), "a/b", NonEmptyList.one(GET), ""),
//      Resource(VariableName("aBCD"), "c/d", NonEmptyList.one(GET), "aB")
//    )
//
//    val resources = Resource.generate(tree)
//    assert(expected.equals(resources))
//  }
//
//  test("parameter and fixed segment with the same name") {
//    val tree = List(
//      Node(
//        Fixed("hello").get,
//        List(GET)
//      ),
//      Node(
//        Parameter("hello").get,
//        List(GET)
//      )
//    )
//
//    val expected = List(
//      Resource(VariableName("hello"), "hello", NonEmptyList.one(GET), ""),
//      Resource(VariableName("hello", 1), "{hello}", NonEmptyList.one(GET), ""),
//    )
//
//    val resources = Resource.generate(tree)
//    assert(expected.equals(resources))
//  }
//
//  test("numeric segment") {
//    val tree = List(
//      Node(
//        Fixed("1").get,
//        List(GET)
//      )
//    )
//
//    val expected = List(
//      Resource(VariableName("p1"), "1", NonEmptyList.one(GET), "") // expect prefix
//    )
//
//    val resources = Resource.generate(tree)
//    assert(expected.equals(resources))
//  }
//
//  test("nested numeric segments that leads to non unique names") {
//    val tree = List(
//      Node(
//        Fixed("1").get,
//        List(GET),
//        List(
//          Node(
//            Fixed("1").get,
//            List(GET)
//          )
//        )
//      ),
//      Node(
//        Fixed("11").get,
//        List(GET)
//      )
//    )
//
//    val expected = List(
//      Resource(VariableName("p1"), "1", NonEmptyList.one(GET), ""),
//      Resource(VariableName("p11"), "1", NonEmptyList.one(GET), "p1"),
//      Resource(VariableName("p11", 1), "11", NonEmptyList.one(GET), "")
//    )
//
//    val resources = Resource.generate(tree)
//    assert(expected.equals(resources))
//  }
//
//  test("increasing suffix by more than one") {
//    val tree = List(
//      Node(
//        Fixed("12").get,
//        List.empty,
//        List(
//          Node(
//            Fixed("3").get,
//            List(GET)
//          )
//        )
//      ),
//      Node(
//        Fixed("123").get,
//        List(GET)
//      ),
//      Node(
//        Fixed("1").get,
//        List.empty,
//        List(
//          Node(
//            Fixed("23").get,
//            List(GET)
//          )
//        )
//      ),
//      Node(
//        Fixed("1").get,
//        List.empty,
//        List(
//          Node(
//            Fixed("2").get,
//            List.empty,
//            List(
//              Node(
//                Fixed("3").get,
//                List(GET)
//              )
//            )
//          )
//        )
//      )
//    )
//
//    val expected = List(
//      Resource(VariableName("p123"), "12/3", NonEmptyList.one(GET), ""),
//      Resource(VariableName("p123", 1), "123", NonEmptyList.one(GET), ""),
//      Resource(VariableName("p123", 2), "1/23", NonEmptyList.one(GET), ""),
//      Resource(VariableName("p123", 3), "1/2/3", NonEmptyList.one(GET), "")
//    )
//
//    val resources = Resource.generate(tree)
//    assert(expected.equals(resources))
//  }
//
//  test("encoded segment") {
//    val tree = List(
//      Node(
//        Fixed("hello").get,
//        List(GET),
//        List(
//          Node(
//            Fixed("bob%3F").get,
//            List(GET)
//          )
//        )
//      )
//    )
//
//    val expected = List(
//      Resource(VariableName("hello"), "hello", NonEmptyList.one(GET), ""),
//      Resource(VariableName("helloBob"), "bob%3F", NonEmptyList.one(GET), "hello")
//    )
//
//    val resources = Resource.generate(tree)
//    assert(expected.equals(resources))
//  }
//
//  test("stripped repeated") {
//    val tree = List(
//      Node(
//        Fixed("hello").get,
//        List(GET)
//      ),
//      Node(
//        Fixed("hello!").get,
//        List(GET)
//      ),
//      Node(
//        Fixed("hello?").get,
//        List(GET)
//      )
//    )
//
//    val expected = List(
//      Resource(VariableName("hello"), "hello", NonEmptyList.one(GET), ""),
//      Resource(VariableName("hello", 1), "hello!", NonEmptyList.one(GET), ""),
//      Resource(VariableName("hello", 2), "hello?", NonEmptyList.one(GET), "")
//    )
//
//    val resources = Resource.generate(tree)
//    assert(expected.equals(resources))
//  }
//
//  // todo: test non suppoerted method
//
//  test("many methods") {
//    val tree = List(
//      Node(
//        Fixed("hello").get,
//        List(GET, POST)
//      )
//    )
//
//    val expected = List(
//      Resource(VariableName("hello"), "hello", NonEmptyList.of(GET, POST), "")
//    )
//
//    val resources = Resource.generate(tree)
//    assert(expected.equals(resources))
//  }
//
//  test("many methods with different order") {
//    val tree = List(
//      Node(
//        Fixed("hello").get,
//        List(POST, GET)
//      )
//    )
//
//    val expected = List(
//      Resource(VariableName("hello"), "hello", NonEmptyList.of(POST, GET), "")
//    )
//
//    val resources = Resource.generate(tree)
//    assert(expected.equals(resources))
//  }
//
//  test("duplicated methods") {
//    val tree = List(
//      Node(
//        Fixed("hello").get,
//        List(POST, POST)
//      )
//    )
//
//    val expected = List(
//      Resource(VariableName("hello"), "hello", NonEmptyList.one(POST), "")
//    )
//
//    val resources = Resource.generate(tree)
//    assert(expected.equals(resources))
//  }

  // todo: check if skipped parts does not add to variables registry
}
