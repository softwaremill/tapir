package sttp.tapir.codegen.testutils

class CompileCheckTestBaseSpec extends CompileCheckTestBase {
  // Sure why not test the test :D

  it should "compile valid code" in {
    compile("object MyObj {}") shouldBe util.Success({})
  }

  it should "not compile invalid code" in {
    compile("asdf") shouldBe a[util.Failure[?]]
  }

  it should "work with an extender too" in {
    "object MyObj {}" shouldCompile ()
  }

  it should "compile full files" in {
    """package test
      |object Asd{}
      |object Bsd{
      |}""".stripMargin shouldCompile ()
  }

  it should "compile with imports" in {
    """object Asd{
      |  import scala.util._
      |  Try(1/0)
      |}""".stripMargin shouldCompile ()
  }

  it should "compile with implicit imports" in {
    """object Asd{
      |  import scala.concurrent.Future
      |  import scala.concurrent.ExecutionContext.Implicits._
      |  Future(1/0)
      |}""".stripMargin shouldCompile ()
  }

  it should "compile code with tapir imports" in {
    """object Asd{
      |  import sttp.tapir._
      |  import sttp.tapir.json.circe._
      |  import sttp.tapir.generic.auto._
      |  import io.circe.generic.auto._
      |  case class Book(title: String)
      |  endpoint.get.in("books" / "my").out(jsonBody[List[Book]])
      |}""".stripMargin shouldCompile ()
  }

  it should "compile code with pure defs/vals" in {
    """val x = 5
      |def q = 4/2
      |""".stripMargin shouldCompile ()
  }
}
