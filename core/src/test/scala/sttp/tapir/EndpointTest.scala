package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.Streams
import sttp.model.{Method, StatusCode}
import sttp.tapir.Schema.SName
import sttp.tapir.internal._
import sttp.tapir.server.{PartialServerEndpoint, ServerEndpoint}

import java.nio.charset.StandardCharsets
import scala.concurrent.Future

class EndpointTest extends AnyFlatSpec with EndpointTestExtensions with Matchers {
  "endpoint" should "compile inputs" in {
    endpoint.in(query[String]("q1")): Endpoint[String, Unit, Unit, Any]
    endpoint.in(query[String]("q1").and(query[Int]("q2"))): Endpoint[(String, Int), Unit, Unit, Any]

    endpoint.in(header[String]("h1")): Endpoint[String, Unit, Unit, Any]
    endpoint.in(header[String]("h1").and(header[Int]("h2"))): Endpoint[(String, Int), Unit, Unit, Any]

    endpoint.in("p" / "p2" / "p3"): Endpoint[Unit, Unit, Unit, Any]
    endpoint.in("p" / "p2" / "p3" / path[String]): Endpoint[String, Unit, Unit, Any]
    endpoint.in("p" / "p2" / "p3" / path[String] / path[Int]): Endpoint[(String, Int), Unit, Unit, Any]

    endpoint.in(stringBody): Endpoint[String, Unit, Unit, Any]
    endpoint.in(stringBody).in(path[Int]): Endpoint[(String, Int), Unit, Unit, Any]
  }

  trait TestStreams extends Streams[TestStreams] {
    override type BinaryStream = Vector[Byte]
    override type Pipe[X, Y] = Nothing
  }
  object TestStreams extends TestStreams

  it should "compile inputs with streams" in {
    endpoint.in(streamBinaryBody(TestStreams)): Endpoint[Vector[Byte], Unit, Unit, TestStreams]
    endpoint
      .in(streamBinaryBody(TestStreams))
      .in(path[Int]): Endpoint[(Vector[Byte], Int), Unit, Unit, TestStreams]
  }

  it should "compile outputs" in {
    endpoint.out(header[String]("h1")): Endpoint[Unit, Unit, String, Any]
    endpoint.out(header[String]("h1").and(header[Int]("h2"))): Endpoint[Unit, Unit, (String, Int), Any]

    endpoint.out(stringBody): Endpoint[Unit, Unit, String, Any]
    endpoint.out(stringBody).out(header[Int]("h1")): Endpoint[Unit, Unit, (String, Int), Any]
  }

  it should "compile outputs with streams" in {
    endpoint.out(streamBinaryBody(TestStreams)): Endpoint[Unit, Unit, Vector[Byte], TestStreams]
    endpoint
      .out(streamBinaryBody(TestStreams))
      .out(header[Int]("h1")): Endpoint[Unit, Unit, (Vector[Byte], Int), TestStreams]
  }

  it should "compile error outputs" in {
    endpoint.errorOut(header[String]("h1")): Endpoint[Unit, String, Unit, Any]
    endpoint.errorOut(header[String]("h1").and(header[Int]("h2"))): Endpoint[Unit, (String, Int), Unit, Any]

    endpoint.errorOut(stringBody): Endpoint[Unit, String, Unit, Any]
    endpoint.errorOut(stringBody).errorOut(header[Int]("h1")): Endpoint[Unit, (String, Int), Unit, Any]
  }

  it should "compile one-of empty output" in {
    endpoint.post
      .errorOut(
        sttp.tapir.oneOf(
          oneOfMapping(StatusCode.NotFound, emptyOutput),
          oneOfMapping(StatusCode.Unauthorized, emptyOutput)
        )
      )
  }

  it should "not allow to map status code multiple times to same format same charset" in {
    implicit val codec: Codec[String, String, CodecFormat.TextPlain] = Codec.string

    the[RuntimeException] thrownBy {
      endpoint.get
        .out(
          sttp.tapir.oneOf(
            oneOfMapping(StatusCode.Accepted, stringBody),
            oneOfMapping(StatusCode.Accepted, plainBody)
          )
        )
    }
  }

  it should "not allow to map default status code multiple times to same format same charset" in {
    implicit val codec: Codec[String, String, CodecFormat.TextPlain] = Codec.string

    the[RuntimeException] thrownBy {
      endpoint.get
        .out(
          sttp.tapir.oneOf(
            oneOfDefaultMapping(stringBody),
            oneOfDefaultMapping(plainBody)
          )
        )
    }
  }

  it should "allow to map status code multiple times to same format different charset" in {
    implicit val codec: Codec[String, String, CodecFormat.TextPlain] = Codec.string
    endpoint.get
      .out(
        sttp.tapir.oneOf(
          oneOfMapping(StatusCode.Accepted, anyFromStringBody(codec, StandardCharsets.UTF_8)),
          oneOfMapping(StatusCode.Accepted, anyFromStringBody(codec, StandardCharsets.ISO_8859_1))
        )
      )
  }

  it should "compile one-of empty output of a custom type" in {
    sealed trait Error
    case class BadRequest(message: String) extends Error
    case object NotFound extends Error

    endpoint.post
      .errorOut(
        sttp.tapir.oneOf(
          oneOfMapping(StatusCode.BadRequest, stringBody.map(BadRequest(_))(_.message)),
          oneOfMapping(StatusCode.NotFound, emptyOutputAs(NotFound))
        )
      )
  }

  "oneOfMapping" should "not compile when the type erasure of `T` is different from `T`" in {
    assertDoesNotCompile("""
      case class Wrapper[T](s: T)

      endpoint.post
        .errorOut(
          sttp.tapir.oneOf(
            oneOfMapping(StatusCode.BadRequest, stringBody.map(Wrapper(_))(_.s)),
          )
        )
    """)
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

    def extend[I, E, O](e: Endpoint[I, E, O, Any]): Endpoint[(I, String, String), E, O, Any] = e.in(i34)
    val extended1: Endpoint[(String, String, String), Unit, Unit, Any] = extend(endpoint.in(i1))
    val extended2: Endpoint[((String, String), String, String), Unit, Unit, Any] = extend(endpoint.in(i1.and(i2)))

    pairToTuple(extended1.input) shouldBe ((((), i1), (i3, i4)))
    pairToTuple(extended2.input) shouldBe ((((), (i1, i2)), (i3, i4)))
  }

  it should "combine four inputs in two groups, through an extend method (left)" in {
    val i1 = query[String]("q1")
    val i2 = query[String]("q2")
    val i3 = query[String]("q3")
    val i4 = query[String]("q4")
    val i34 = i3.and(i4)

    def extend[I, E, O](e: Endpoint[I, E, O, Any]): Endpoint[(String, String, I), E, O, Any] = e.prependIn(i34)
    val extended1: Endpoint[(String, String, String), Unit, Unit, Any] = extend(endpoint.in(i1))
    val extended2: Endpoint[(String, String, (String, String)), Unit, Unit, Any] = extend(endpoint.in(i1.and(i2)))

    pairToTuple(extended1.input) shouldBe (((i3, i4), ((), i1)))
    pairToTuple(extended2.input) shouldBe (((i3, i4), ((), (i1, i2))))
  }

  val showTestData = List(
    (endpoint.in("p1/p2"), "/p1%2Fp2 -> -/-"),
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

  val renderTestData = List(
    (endpoint, "/"),
    (endpoint.in("p1"), "/p1"),
    (endpoint.in("p1" / "p2"), "/p1/p2"),
    (endpoint.in("p1" / path[String]), "/p1/{param1}"),
    (endpoint.in("p1" / path[String].name("par")), "/p1/{par}"),
    (endpoint.in("p1" / query[String]("par")), "/p1?par={par}"),
    (endpoint.in("p1" / query[String]("par1") / query[String]("par2")), "/p1?par1={par1}&par2={par2}"),
    (endpoint.in("p1" / path[String].name("par1") / query[String]("par2")), "/p1/{par1}?par2={par2}"),
    (endpoint.in("p1" / auth.apiKey(query[String]("par2"))), "/p1?par2={par2}"),
    (endpoint.in("p2" / path[String]).mapIn(identity(_))(identity(_)), "/p2/{param1}"),
    (endpoint.in("p1/p2"), "/p1%2Fp2")
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
    input.codec.schema.applyValidation(0) should not be empty
    input.codec.schema.applyValidation(4) should not be empty
    input.codec.schema.applyValidation(2) shouldBe empty
  }

  it should "add validator for an option" in {
    val input = query[Option[Int]]("x").validateOption(Validator.min(1))
    input.codec.schema.applyValidation(Some(0)) should not be empty
    input.codec.schema.applyValidation(Some(2)) shouldBe empty
    input.codec.schema.applyValidation(None) shouldBe empty
  }

  it should "add validator for an iterable" in {
    val input = query[List[Int]]("x").validateIterable(Validator.min(1))
    input.codec.schema.applyValidation(List(0)) should not be empty
    input.codec.schema.applyValidation(List(2, 0)) should not be empty
    input.codec.schema.applyValidation(List(2, 2)) shouldBe empty
    input.codec.schema.applyValidation(Nil) shouldBe empty
  }

  it should "map input and output" in {
    case class Wrapper(s: String)

    endpoint.in(query[String]("q1")).mapInTo[Wrapper]: Endpoint[Wrapper, Unit, Unit, Any]
    endpoint.out(stringBody).mapOutTo[Wrapper]: Endpoint[Unit, Unit, Wrapper, Any]
    endpoint.errorOut(stringBody).mapErrorOutTo[Wrapper]: Endpoint[Unit, Wrapper, Unit, Any]
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

  "compile" should "work for endpoint descriptions providing partial server logic using serverLogicForCurrent" in {
    case class User1(x: String, y: Int)
    case class User2(z: Double)
    case class Result(u1: User1, u2: User2, a: String)
    val base: PartialServerEndpoint[(String, Int), User1, Unit, String, Unit, Any, Future] = endpoint
      .errorOut(stringBody)
      .in(query[String]("x"))
      .in(query[Int]("y"))
      .serverLogicForCurrent { case (x, y) => Future.successful(Right(User1(x, y)): Either[String, User1]) }

    implicit val schemaForResult: Schema[Result] = Schema[Result](SchemaType.SProduct(List.empty), Some(SName.Unit))
    implicit val codec: Codec[String, Result, CodecFormat.TextPlain] = Codec.stringCodec(_ => Result(null, null, ""))

    base
      .in(query[Double]("z"))
      .serverLogicForCurrent { z => Future.successful(Right(User2(z)): Either[String, User2]) }
      .in(query[String]("a"))
      .out(plainBody[Result])
      .serverLogic { case ((u1, u2), a) =>
        Future.successful(Right(Result(u1, u2, a)): Either[String, Result])
      }
  }

  "compile" should "work for endpoint descriptions providing partial server logic using serverLogicPart" in {
    case class User1(x: String)
    case class User2(x: Int)
    case class Result(u1: User1, u2: User2, d: Double)

    def parse1(t: String): Future[Either[String, User1]] = Future.successful(Right(User1(t)))
    def parse2(t: Int): Future[Either[String, User2]] = Future.successful(Right(User2(t)))

    implicit val schemaForResult: Schema[Result] = Schema[Result](SchemaType.SProduct(List.empty), Some(SName.Unit))
    implicit val codec: Codec[String, Result, CodecFormat.TextPlain] = Codec.stringCodec(_ => Result(null, null, 0d))

    val _: ServerEndpoint[(String, Int, Double), String, Result, Any, Future] = endpoint
      .in(query[String]("x"))
      .in(query[Int]("y"))
      .in(query[Double]("z"))
      .errorOut(stringBody)
      .out(plainBody[Result])
      .serverLogicPart(parse1)
      .andThenPart(parse2)
      .andThen { case ((user1, user2), d) =>
        Future.successful(Right(Result(user1, user2, d)): Either[String, Result])
      }
  }

  "PartialServerEndpoint" should "include all inputs when recovering the endpoint" in {
    val pe: PartialServerEndpoint[String, String, Unit, Int, Unit, Any, Future] =
      endpoint
        .in("secure")
        .in(query[String]("token"))
        .errorOut(plainBody[Int])
        .serverLogicForCurrent(_ => Future.successful(Right(""): Either[Int, String]))

    val e = pe.get
      .in("hello")
      .in(query[String]("salutation"))
      .out(stringBody)
      .endpoint

    val basicInputs = e.input.asVectorOfBasicInputs()
    basicInputs.filter {
      case EndpointInput.Query("token", _, _)      => true
      case EndpointInput.Query("salutation", _, _) => true
      case _                                       => false
    } should have size (2)
  }

  "mapTo" should "properly map between tuples and case classes of arity 1" in {
    // given
    case class Wrapper(i: Int)
    val mapped = query[Int]("q1").mapTo[Wrapper]
    val codec: Codec[List[String], Wrapper, CodecFormat.TextPlain] = mapped.codec

    // when
    codec.encode(Wrapper(10)) shouldBe (List("10"))
    codec.decode(List("10")) shouldBe DecodeResult.Value(Wrapper(10))
  }

  "mapTo" should "properly map between tuples and case classes of arity 2" in {
    // given
    case class Wrapper(i: Int, s: String)
    val mapped = query[Int]("q1").and(query[String]("q2")).mapTo[Wrapper]
    val mapping: Mapping[(Int, String), Wrapper] = mapped match {
      case EndpointInput.MappedPair(_, m) => m.asInstanceOf[Mapping[(Int, String), Wrapper]]
      case _                              => fail()
    }

    // when
    mapping.encode(Wrapper(10, "x")) shouldBe ((10, "x"))
    mapping.decode((10, "x")) shouldBe DecodeResult.Value(Wrapper(10, "x"))
  }

  "mapTo" should "fail on invalid field type" in {
    assertDoesNotCompile("""
      |case class Wrapper(i: Int, i2: Int)
      |query[Int]("q1").and(query[String]("q2")).mapTo[Wrapper]
    """)
  }

  "mapTo" should "fail on redundant field" in {
    assertDoesNotCompile("""
      |case class Wrapper(i: Int, s: String, c: Char)
      |query[Int]("q1").and(query[String]("q2")).mapTo[Wrapper]
    """)
  }

  "mapTo" should "fail on missing field" in {
    assertDoesNotCompile("""
      |case class Wrapper(i: Int)
      |query[Int]("q1").and(query[String]("q2")).mapTo[Wrapper]
    """)
  }

  "mapTo" should "compile for case class with defined companion object" in {
    assertCompiles("""
      import sttp.tapir.generic.auto._

      case class Wrapper(i: Int, s: String)
      object Wrapper

      formBody[Wrapper]
    """)
  }
}
