package sttp.tapir

import org.scalatest.{FlatSpec, Matchers}
import sttp.model.{Method, StatusCode}
import sttp.tapir.server.{PartialServerEndpoint, ServerEndpoint}
import sttp.tapir.util.CompileUtil

import scala.concurrent.Future

class EndpointTest extends FlatSpec with Matchers {
  "endpoint" should "compile inputs" in {
    endpoint.in(query[String]("q1")): Endpoint[String, Unit, Unit, Nothing]
    endpoint.in(query[String]("q1").and(query[Int]("q2"))): Endpoint[(String, Int), Unit, Unit, Nothing]

    endpoint.in(header[String]("h1")): Endpoint[String, Unit, Unit, Nothing]
    endpoint.in(header[String]("h1").and(header[Int]("h2"))): Endpoint[(String, Int), Unit, Unit, Nothing]

    endpoint.in("p" / "p2" / "p3"): Endpoint[Unit, Unit, Unit, Nothing]
    endpoint.in("p" / "p2" / "p3" / path[String]): Endpoint[String, Unit, Unit, Nothing]
    endpoint.in("p" / "p2" / "p3" / path[String] / path[Int]): Endpoint[(String, Int), Unit, Unit, Nothing]

    endpoint.in(stringBody): Endpoint[String, Unit, Unit, Nothing]
    endpoint.in(stringBody).in(path[Int]): Endpoint[(String, Int), Unit, Unit, Nothing]
  }

  it should "compile inputs with streams" in {
    endpoint.in(streamBody[Vector[Byte]](schemaFor[String], CodecFormat.Json())): Endpoint[Vector[Byte], Unit, Unit, Vector[Byte]]
    endpoint
      .in(streamBody[Vector[Byte]](schemaFor[String], CodecFormat.Json()))
      .in(path[Int]): Endpoint[(Vector[Byte], Int), Unit, Unit, Vector[Byte]]
  }

  it should "compile outputs" in {
    endpoint.out(header[String]("h1")): Endpoint[Unit, Unit, String, Nothing]
    endpoint.out(header[String]("h1").and(header[Int]("h2"))): Endpoint[Unit, Unit, (String, Int), Nothing]

    endpoint.out(stringBody): Endpoint[Unit, Unit, String, Nothing]
    endpoint.out(stringBody).out(header[Int]("h1")): Endpoint[Unit, Unit, (String, Int), Nothing]
  }

  it should "compile outputs with streams" in {
    endpoint.out(streamBody[Vector[Byte]](schemaFor[String], CodecFormat.Json())): Endpoint[Unit, Unit, Vector[Byte], Vector[Byte]]
    endpoint
      .out(streamBody[Vector[Byte]](schemaFor[String], CodecFormat.Json()))
      .out(header[Int]("h1")): Endpoint[Unit, Unit, (Vector[Byte], Int), Vector[Byte]]
  }

  it should "compile error outputs" in {
    endpoint.errorOut(header[String]("h1")): Endpoint[Unit, String, Unit, Nothing]
    endpoint.errorOut(header[String]("h1").and(header[Int]("h2"))): Endpoint[Unit, (String, Int), Unit, Nothing]

    endpoint.errorOut(stringBody): Endpoint[Unit, String, Unit, Nothing]
    endpoint.errorOut(stringBody).errorOut(header[Int]("h1")): Endpoint[Unit, (String, Int), Unit, Nothing]
  }

  it should "compile one-of empty output" in {
    endpoint.post
      .errorOut(
        sttp.tapir.oneOf(
          statusMapping(StatusCode.NotFound, emptyOutput),
          statusMapping(StatusCode.Unauthorized, emptyOutput)
        )
      )
  }

  it should "not compile invalid outputs with queries" in {
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

  def pairToTuple(input: EndpointInput[_]): Any =
    input match {
      case EndpointInput.Pair(left, right, _, _) => (pairToTuple(left), pairToTuple(right))
      case EndpointIO.Pair(left, right, _, _)    => (pairToTuple(left), pairToTuple(right))
      case EndpointIO.Empty(_, _)                => ()
      case i                                     => i
    }

  it should "combine two inputs" in {
    val i1 = query[String]("q1")
    val i2 = query[String]("q2")
    pairToTuple(endpoint.in(i1).in(i2).input) shouldBe ((((), i1), i2))
  }

  it should "combine four inputs in two groups" in {
    val i1 = query[String]("q1")
    val i2 = query[String]("q2")
    val i3 = query[String]("q3")
    val i4 = query[String]("q4")
    pairToTuple(endpoint.in(i1.and(i2)).in(i3.and(i4)).input) shouldBe ((((), (i1, i2)), (i3, i4)))
  }

  it should "combine four inputs in two groups, through an extend method (right)" in {
    val i1 = query[String]("q1")
    val i2 = query[String]("q2")
    val i3 = query[String]("q3")
    val i4 = query[String]("q4")
    val i34 = i3.and(i4)

    def extend[I, E, O](e: Endpoint[I, E, O, Nothing]): Endpoint[(I, String, String), E, O, Nothing] = e.in(i34)
    val extended1: Endpoint[(String, String, String), Unit, Unit, Nothing] = extend(endpoint.in(i1))
    val extended2: Endpoint[((String, String), String, String), Unit, Unit, Nothing] = extend(endpoint.in(i1.and(i2)))

    pairToTuple(extended1.input) shouldBe ((((), i1), (i3, i4)))
    pairToTuple(extended2.input) shouldBe ((((), (i1, i2)), (i3, i4)))
  }

  it should "combine four inputs in two groups, through an extend method (left)" in {
    val i1 = query[String]("q1")
    val i2 = query[String]("q2")
    val i3 = query[String]("q3")
    val i4 = query[String]("q4")
    val i34 = i3.and(i4)

    def extend[I, E, O](e: Endpoint[I, E, O, Nothing]): Endpoint[(String, String, I), E, O, Nothing] = e.prependIn(i34)
    val extended1: Endpoint[(String, String, String), Unit, Unit, Nothing] = extend(endpoint.in(i1))
    val extended2: Endpoint[(String, String, (String, String)), Unit, Unit, Nothing] = extend(endpoint.in(i1.and(i2)))

    pairToTuple(extended1.input) shouldBe (((i3, i4), ((), i1)))
    pairToTuple(extended2.input) shouldBe (((i3, i4), ((), (i1, i2))))
  }

  val showTestData = List(
    (endpoint.name("E1").in("p1"), "[E1] /p1 -> -/-"),
    (endpoint.get.in("p1" / "p2"), "GET /p1 /p2 -> -/-"),
    (endpoint.in("p1" / path[String]("p2") / paths), "/p1 /[p2] /... -> -/-"),
    (
      endpoint.post.in(query[String]("q1")).in(query[Option[Int]]("q2")).in(stringBody).errorOut(stringBody),
      "POST ?q1 ?q2 {body as text/plain (UTF-8)} -> {body as text/plain (UTF-8)}/-"
    ),
    (endpoint.get.in(header[String]("X-header")).out(header[String]("Y-header")), "GET {header X-header} -> -/{header Y-header}")
  )

  for ((testShowEndpoint, expectedShowResult) <- showTestData) {
    s"show for ${testShowEndpoint.showDetail}" should s"be $expectedShowResult" in {
      testShowEndpoint.show shouldBe expectedShowResult
    }
  }

  import sttp.tapir.bimap.syntax._
  val renderTestData = List(
    (endpoint, "/"),
    (endpoint.in("p1"), "/p1"),
    (endpoint.in("p1" / "p2"), "/p1/p2"),
    (endpoint.in("p1" / path[String]), "/p1/{param1}"),
    (endpoint.in("p1" / path[String].name("par")), "/p1/{par}"),
    (endpoint.in("p1" / pathFromStringBiMap(Map("s" -> 1).toBiMap)), "/p1/{param1}"),
    (endpoint.in("p1" / query[String]("par")), "/p1?par={par}"),
    (endpoint.in("p1" / query[String]("par1") / query[String]("par2")), "/p1?par1={par1}&par2={par2}"),
    (endpoint.in("p1" / path[String].name("par1") / query[String]("par2")), "/p1/{par1}?par2={par2}"),
    (endpoint.in("p1" / auth.apiKey(query[String]("par2"))), "/p1?par2={par2}"),
    (endpoint.in("p2" / path[String]).mapIn(identity(_))(identity(_)), "/p2/{param1}")
  )

  for ((testEndpoint, expectedRenderPath) <- renderTestData) {
    s"renderPath for ${testEndpoint.showDetail}" should s"be $expectedRenderPath" in {
      testEndpoint.renderPathTemplate() shouldBe expectedRenderPath
    }
  }

  "renderPath" should "keep param count in render functions" in {
    val testEndpoint = endpoint.in("p1" / path[String] / query[String]("param"))
    testEndpoint.renderPathTemplate(
      renderPathParam = (index, _) => s"{par$index}",
      renderQueryParam = Some((index, query) => s"${query.name}={par$index}")
    ) shouldBe "/p1/{par1}?param={par2}"
  }

  "validate" should "accumulate validators" in {
    val input = query[Int]("x").validate(Validator.min(1)).validate(Validator.max(3))
    input.codec.validator.validate(0) should not be empty
    input.codec.validator.validate(4) should not be empty
    input.codec.validator.validate(2) shouldBe empty
  }

  val httpMethodTestData = List(
    endpoint -> None,
    endpoint.in("api" / "cats" / path[String]).get -> Some(Method.GET),
    endpoint.in("api" / "cats" / path[String]).put -> Some(Method.PUT),
    endpoint.in("api" / "cats" / path[String]).post -> Some(Method.POST),
    endpoint.in("api" / "cats" / path[String]).head -> Some(Method.HEAD),
    endpoint.in("api" / "cats" / path[String]).trace -> Some(Method.TRACE),
    endpoint.in("api" / "cats" / path[String]).patch -> Some(Method.PATCH),
    endpoint.in("api" / "cats" / path[String]).connect -> Some(Method.CONNECT),
    endpoint.in("api" / "cats" / path[String]).delete -> Some(Method.DELETE),
    endpoint.in("api" / "cats" / path[String]).options -> Some(Method.OPTIONS),
    endpoint.in("api" / "cats" / path[String]).method(Method("XX")) -> Some(Method("XX"))
  )

  for ((testEndpoint, expectedMethod) <- httpMethodTestData) {
    s"httpMethod for ${testEndpoint.showDetail}" should s"be $expectedMethod" in {
      testEndpoint.httpMethod shouldBe expectedMethod
    }
  }

  it should "compile endpoint descriptions providing partial server logic using serverLogicForCurrent" in {
    case class User1(x: String, y: Int)
    case class User2(z: Double)
    case class Result(u1: User1, u2: User2, a: String)
    val base: PartialServerEndpoint[User1, Unit, String, Unit, Nothing, Future] = endpoint
      .errorOut(stringBody)
      .in(query[String]("x"))
      .in(query[Int]("y"))
      .serverLogicForCurrent { case (x, y) => Future.successful(Right(User1(x, y)): Either[String, User1]) }

    base
      .in(query[Double]("z"))
      .serverLogicForCurrent { z => Future.successful(Right(User2(z)): Either[String, User2]) }
      .in(query[String]("a"))
      .out(plainBody[Result](null: Codec[String, Result, CodecFormat.TextPlain]))
      .serverLogic {
        case ((u1, u2), a) => Future.successful(Right(Result(u1, u2, a)): Either[String, Result])
      }
  }

  it should "compile endpoint descriptions providing partial server logic using serverLogicPart" in {
    case class User1(x: String)
    case class User2(x: Int)
    case class Result(u1: User1, u2: User2, d: Double)

    def parse1(t: String): Future[Either[String, User1]] = Future.successful(Right(User1(t)))
    def parse2(t: Int): Future[Either[String, User2]] = Future.successful(Right(User2(t)))

    val _: ServerEndpoint[(String, Int, Double), String, Result, Nothing, Future] = endpoint
      .in(query[String]("x"))
      .in(query[Int]("y"))
      .in(query[Double]("z"))
      .errorOut(stringBody)
      .out(plainBody[Result](null: Codec[String, Result, CodecFormat.TextPlain]))
      .serverLogicPart(parse1)
      .andThenPart(parse2)
      .andThen {
        case ((user1, user2), d) => Future.successful(Right(Result(user1, user2, d)): Either[String, Result])
      }
  }
}
