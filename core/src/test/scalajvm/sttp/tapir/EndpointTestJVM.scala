package sttp.tapir

import sttp.tapir.util.CompileUtil

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class EndpointTestJVM extends AnyFlatSpec with Matchers {
  "endpoint" should "not compile invalid outputs with queries" in {
    val exception = CompileUtil.interceptEval("""import sttp.tapir._
                                                |endpoint.out(query[String]("q1"))""".stripMargin)

    exception.getMessage contains "found   : tapir.EndpointInput.Query[String]"
    exception.getMessage contains "required: tapir.EndpointIO[?]"
  }

  it should "not compile invalid outputs with paths" in {
    val exception = CompileUtil.interceptEval("""import sttp.tapir._
                                                |endpoint.out(path[String])""".stripMargin)

    exception.getMessage contains "found   : tapir.EndpointInput.PathCapture[String]"
    exception.getMessage contains "required: tapir.EndpointIO[?]"
  }
}
