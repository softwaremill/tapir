package sttp.tapir.serverless.aws.cdk.internal

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir.serverless.aws.cdk.internal.Method._
import sttp.tapir.serverless.aws.cdk.internal.Segment._

class TreeTest extends AnyFunSuite with Matchers {

  test("dependant nodes") {
    val requests = List(
      List("country", "{countryId}", "province", "{provinceId}").toRequest(GET),
      List("country", "{countryId}", "province", "{provinceId}", "governor").toRequest(GET),
      List("product", "{id}", "tariff").toRequest(GET)
    )

    val expected =
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

    val tree = Tree.fromRequests(requests)
    assert(expected.equals(tree))
  }

  test("from flat requests hierarchy") {
    val requests = List(
      List("a").toRequest(GET),
      List("b").toRequest(GET),
      List("c").toRequest(GET)
    )

    val expected = List(
      Node(Fixed("a").get, List(GET)),
      Node(Fixed("b").get, List(GET)),
      Node(Fixed("c").get, List(GET))
    )

    assert(expected.equals(Tree.fromRequests(requests)))
  }

  test("segments on the shared level") {
    val requests = List(
      List("book", "{id}").toRequest(GET),
      List("book", "{id}", "authors").toRequest(GET),
      List("book", "{id}", "related").toRequest(GET)
    )

    val expected =
      List(
        Node(
          Fixed("book").get,
          List.empty,
          List(
            Node(
              Parameter("id").get,
              List(GET),
              List(
                Node(
                  Fixed("authors").get,
                  List(GET)
                ),
                Node(
                  Fixed("related").get,
                  List(GET)
                )
              )
            )
          )
        )
      )

    assert(expected.equals(Tree.fromRequests(requests)))
  }

  test("multiple methods") {
    val requests = List(
      List("book", "{id}").toRequest(DELETE),
      List("book").toRequest(POST),
      List("book", "{id}").toRequest(GET)
    )

    val expected =
      List(
        Node(
          Fixed("book").get,
          List(POST),
          List(
            Node(
              Parameter("id").get,
              List(GET, DELETE)
            )
          )
        )
      )

    assert(expected.equals(Tree.fromRequests(requests)))
  }

  test("multiple methods order") { // different order
    val urls = List(
      List("book").toRequest(POST),
      List("book", "{id}").toRequest(GET),
      List("book", "{id}").toRequest(DELETE)
    )

    val expected =
      List(
        Node(
          Fixed("book").get,
          List(POST),
          List(
            Node(
              Parameter("id").get,
              List(GET, DELETE)
            )
          )
        )
      )

    val tree = Tree.fromRequests(urls)
    assert(expected.equals(tree))
  }

  test("multiple methods with mixed order") {
    val urls = List(
      List("book").toRequest(POST),
      List("book", "{id}").toRequest(DELETE), // different order
      List("book", "{id}").toRequest(GET)
    )

    val expected =
      List(
        Node(
          Fixed("book").get,
          List(POST),
          List(
            Node(
              Parameter("id").get,
              List(GET, DELETE)
            )
          )
        )
      )

    assert(expected.equals(Tree.fromRequests(urls)))
  }

  test("parameter and fixed segment with the same name") {
    val requests = List(
      List("a").toRequest(GET),
      List("{a}").toRequest(GET)
    )

    val expected = List(
      Node(Fixed("a").get, List(GET)),
      Node(Parameter("a").get, List(GET))
    )

    val tree = Tree.fromRequests(requests)
    assert(expected.equals(tree))
  }

  test("Tree is empty when there are no request to process") {
    val requests = List.empty[Request]
    assert(Tree.fromRequests(requests) == List.empty)
  }
}
