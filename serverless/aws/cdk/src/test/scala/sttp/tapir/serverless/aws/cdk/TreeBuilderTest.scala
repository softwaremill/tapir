package sttp.tapir.serverless.aws.cdk

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class TreeBuilderTest extends AnyFunSuite with Matchers {

  test("new synewewetax 2") { //fixme rename all those tests
    val urls = List(
      Url("GET", List("country", "{countryId}", "province", "{provinceId}").toSegments),
      Url("GET", List("country", "{countryId}", "province", "{provinceId}", "governor").toSegments),
      Url("GET", List("product", "{id}", "tariff").toSegments)
    )

    val expected =
      List(
        Node(
          Fixed("country"),
          List.empty,
          List(
            Node(
              Parameter("countryId"),
              List.empty,
              List(
                Node(
                  Fixed("province"),
                  List.empty,
                  List(
                    Node(
                      Parameter("provinceId"),
                      List("GET"),
                      List(
                        Node(
                          Fixed("governor"),
                          List("GET")
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
          Fixed("product"),
          List.empty,
          List(
            Node(
              Parameter("id"),
              List.empty,
              List(
                Node(
                  Fixed("tariff"),
                  List("GET")
                )
              )
            )
          )
        )
      )

    val result = TreeBuilder.run(urls)

    assert(expected.toString() == result.toString())
    assert(expected.equals(result))
  }

  test("new s3223yntax 2") {
    val urls = List(
      Url("GET", List("a").toSegments),
      Url("GET", List("b").toSegments),
      Url("GET", List("c").toSegments)
    )

    val expected = List(
      Node(Fixed("a"), List("GET")),
      Node(Fixed("b"), List("GET")),
      Node(Fixed("c"), List("GET"))
    )

    val result = TreeBuilder.run(urls)

    assert(expected.equals(result))
  }

  test("new syntax 2") {
    val urls = List(
      Url("GET", List("book", "{id}").toSegments),
      Url("GET", List("book", "{id}", "authors").toSegments),
      Url("GET", List("book", "{id}", "related").toSegments)
    )

    val expected =
      List(
        Node(
          Fixed("book"),
          List.empty,
          List(
            Node(
              Parameter("id"),
              List("GET"),
              List(
                Node(
                  Fixed("authors"),
                  List("GET")
                ),
                Node(
                  Fixed("related"),
                  List("GET")
                )
              )
            )
          )
        )
      )

    val result = TreeBuilder.run(urls)

    assert(expected.equals(result))
  }

  test("new syntax 21") {
    val urls = List(
      Url("GET", List("book", "{id}").toSegments),
      Url("POST", List("book").toSegments),
      Url("DELETE", List("book", "{id}").toSegments)
    )

    val expected =
      List(
        Node(
          Fixed("book"),
          List("POST"),
          List(
            Node(
              Parameter("id"),
              List("DELETE", "GET")
            )
          )
        )
      )

    val result = TreeBuilder.run(urls)

    assert(expected.equals(result))
  }

  test("new syntax 2221") { // different order
    val urls = List(
      Url("POST", List("book").toSegments),
      Url("GET", List("book", "{id}").toSegments),
      Url("DELETE", List("book", "{id}").toSegments)
    )

    val expected =
      List(
        Node(
          Fixed("book"),
          List("POST"),
          List(
            Node(
              Parameter("id"),
              List("DELETE", "GET")
            )
          )
        )
      )

    val result = TreeBuilder.run(urls)

    assert(expected.equals(result))
  }

  test("new syntax 22221") {
    val urls = List(
      Url("POST", List("book").toSegments),
      Url("DELETE", List("book", "{id}").toSegments), // different order
      Url("GET", List("book", "{id}").toSegments)
    )

    val expected =
      List(
        Node(
          Fixed("book"),
          List("POST"),
          List(
            Node(
              Parameter("id"),
              List("DELETE", "GET")
            )
          )
        )
      )

    val result = TreeBuilder.run(urls)

    assert(expected.equals(result))
  }
}
