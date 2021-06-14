package sttp.tapir

trait EndpointTestExtensions { self: EndpointTest =>
  "endpoint" should "not compile invalid outputs with queries" in {
    assertTypeError("""import sttp.tapir._
                      |endpoint.out(query[String]("q1"))""".stripMargin)
  }

  it should "not compile invalid outputs with paths" in {
    assertTypeError("""import sttp.tapir._
                      |endpoint.out(path[String])""".stripMargin)
  }
}
