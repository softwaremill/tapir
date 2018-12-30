package tapir

import org.scalatest.{FlatSpec, Matchers}
import tapir.util.CompileUtil

class EndpointTest extends FlatSpec with Matchers {
  it should "compile inputs" in {
    endpoint.in(query[String]("q1")): Endpoint[String, Unit, Unit]
    endpoint.in(query[String]("q1").and(query[Int]("q2"))): Endpoint[(String, Int), Unit, Unit]

    endpoint.in(header[String]("h1")): Endpoint[String, Unit, Unit]
    endpoint.in(header[String]("h1").and(header[Int]("h2"))): Endpoint[(String, Int), Unit, Unit]

    endpoint.in("p" / "p2" / "p3"): Endpoint[Unit, Unit, Unit]
    endpoint.in("p" / "p2" / "p3" / path[String]): Endpoint[String, Unit, Unit]
    endpoint.in("p" / "p2" / "p3" / path[String] / path[Int]): Endpoint[(String, Int), Unit, Unit]

    endpoint.in(stringBody): Endpoint[String, Unit, Unit]
    endpoint.in(stringBody).in(path[Int]): Endpoint[(String, Int), Unit, Unit]
  }

  it should "compile outputs" in {
    endpoint.out(header[String]("h1")): Endpoint[Unit, Unit, String]
    endpoint.out(header[String]("h1").and(header[Int]("h2"))): Endpoint[Unit, Unit, (String, Int)]

    endpoint.out(stringBody): Endpoint[Unit, Unit, String]
    endpoint.out(stringBody).out(header[Int]("h1")): Endpoint[Unit, Unit, (String, Int)]
  }

  it should "compile error outputs" in {
    endpoint.errorOut(header[String]("h1")): Endpoint[Unit, String, Unit]
    endpoint.errorOut(header[String]("h1").and(header[Int]("h2"))): Endpoint[Unit, (String, Int), Unit]

    endpoint.errorOut(stringBody): Endpoint[Unit, String, Unit]
    endpoint.errorOut(stringBody).errorOut(header[Int]("h1")): Endpoint[Unit, (String, Int), Unit]
  }

  it should "not compile invalid outputs with queries" in {
    val exception = CompileUtil.interceptEval("""import tapir._
                                                |endpoint.out(query[String]("q1"))""".stripMargin)

    exception.getMessage contains "found   : tapir.EndpointInput.Query[String]"
    exception.getMessage contains "required: tapir.EndpointIO[?]"
  }

  it should "not compile invalid outputs with paths" in {
    val exception = CompileUtil.interceptEval("""import tapir._
                                                |endpoint.out(path[String])""".stripMargin)

    exception.getMessage contains "found   : tapir.EndpointInput.PathCapture[String]"
    exception.getMessage contains "required: tapir.EndpointIO[?]"
  }
}
