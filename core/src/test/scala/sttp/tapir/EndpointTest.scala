package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.Streams
import sttp.model.internal.Rfc3986
import sttp.model.{Method, StatusCode}
import sttp.tapir.Schema.SName
import sttp.tapir.server.PartialServerEndpoint

import java.nio.charset.StandardCharsets
import scala.concurrent.Future

class EndpointTest extends AnyFlatSpec with EndpointTestExtensions with Matchers {
  "endpoint" should "compile inputs" in {
    endpoint.in(query[String]("q1")): PublicEndpoint[String, Unit, Unit, Any]
    endpoint.in(query[String]("q1").and(query[Int]("q2"))): PublicEndpoint[(String, Int), Unit, Unit, Any]

    endpoint.in(header[String]("h1")): PublicEndpoint[String, Unit, Unit, Any]
    endpoint.in(header[String]("h1").and(header[Int]("h2"))): PublicEndpoint[(String, Int), Unit, Unit, Any]

    endpoint.in("p" / "p2" / "p3"): PublicEndpoint[Unit, Unit, Unit, Any]
    endpoint.in("p" / "p2" / "p3" / path[String]): PublicEndpoint[String, Unit, Unit, Any]
    endpoint.in("p" / "p2" / "p3" / path[String] / path[Int]): PublicEndpoint[(String, Int), Unit, Unit, Any]

    endpoint.in(stringBody): PublicEndpoint[String, Unit, Unit, Any]
    endpoint.in(stringBody).in(path[Int]): PublicEndpoint[(String, Int), Unit, Unit, Any]
  }

  it should "compile security inputs" in {
    endpoint.securityIn(query[String]("q1")): Endpoint[String, Unit, Unit, Unit, Any]
    endpoint.securityIn(query[String]("q1").and(query[Int]("q2"))): Endpoint[(String, Int), Unit, Unit, Unit, Any]
    endpoint.securityIn(stringBody).securityIn(path[Int]): Endpoint[(String, Int), Unit, Unit, Unit, Any]
  }

  it should "compile map security into case class" in {
    case class Foo(a: String, b: String)
    endpoint.securityIn(auth.bearer[String]()).securityIn(path[String]("foo")).mapSecurityInTo[Foo]
  }

  it should "compile a path fold" in {
    endpoint.in(List("a", "b", "c").foldLeft(emptyInput)(_ / _))
  }

  trait TestStreams extends Streams[TestStreams] {
    override type BinaryStream = Vector[Byte]
    override type Pipe[X, Y] = Nothing
  }
  object TestStreams extends TestStreams

  it should "compile inputs with streams" in {
    endpoint.in(streamBinaryBody(TestStreams)(CodecFormat.OctetStream())): PublicEndpoint[Vector[Byte], Unit, Unit, TestStreams]
    endpoint
      .in(streamBinaryBody(TestStreams)(CodecFormat.OctetStream()))
      .in(path[Int]): PublicEndpoint[(Vector[Byte], Int), Unit, Unit, TestStreams]
  }

  it should "compile outputs" in {
    endpoint.out(header[String]("h1")): PublicEndpoint[Unit, Unit, String, Any]
    endpoint.out(header[String]("h1").and(header[Int]("h2"))): PublicEndpoint[Unit, Unit, (String, Int), Any]

    endpoint.out(stringBody): PublicEndpoint[Unit, Unit, String, Any]
    endpoint.out(stringBody).out(header[Int]("h1")): PublicEndpoint[Unit, Unit, (String, Int), Any]
  }

  it should "compile outputs with streams" in {
    endpoint.out(streamBinaryBody(TestStreams)(CodecFormat.OctetStream())): PublicEndpoint[Unit, Unit, Vector[Byte], TestStreams]
    endpoint
      .out(streamBinaryBody(TestStreams)(CodecFormat.OctetStream()))
      .out(header[Int]("h1")): PublicEndpoint[Unit, Unit, (Vector[Byte], Int), TestStreams]
  }

  it should "compile error outputs" in {
    endpoint.errorOut(header[String]("h1")): PublicEndpoint[Unit, String, Unit, Any]
    endpoint.errorOut(header[String]("h1").and(header[Int]("h2"))): PublicEndpoint[Unit, (String, Int), Unit, Any]

    endpoint.errorOut(stringBody): PublicEndpoint[Unit, String, Unit, Any]
    endpoint.errorOut(stringBody).errorOut(header[Int]("h1")): PublicEndpoint[Unit, (String, Int), Unit, Any]
  }

  it should "compile one-of empty output" in {
    endpoint.post
      .errorOut(
        sttp.tapir.oneOf(
          oneOfVariant(StatusCode.NotFound, emptyOutput),
          oneOfVariant(StatusCode.Unauthorized, emptyOutput)
        )
      )
  }

  it should "allow to map status code multiple times to same format different charset" in {
    implicit val codec: Codec[String, String, CodecFormat.TextPlain] = Codec.string
    endpoint.get
      .out(
        sttp.tapir.oneOf(
          oneOfVariant(StatusCode.Accepted, stringBodyAnyFormat(codec, StandardCharsets.UTF_8)),
          oneOfVariant(StatusCode.Accepted, stringBodyAnyFormat(codec, StandardCharsets.ISO_8859_1))
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
          oneOfVariant(StatusCode.BadRequest, stringBody.map(BadRequest(_))(_.message)),
          oneOfVariant(StatusCode.NotFound, emptyOutputAs(NotFound))
        )
      )
  }

  it should "compile with a single error variant" in {
    sealed trait Error
    case class BadRequest(message: String) extends Error
    case object NotFound extends Error

    val e = endpoint.post
      .errorOut(statusCode(StatusCode.BadRequest).and(stringBody.map(BadRequest(_))(_.message)))
      .errorOutVariant[Error](oneOfVariant(statusCode(StatusCode.NotFound).and(emptyOutputAs(NotFound))))

    e: PublicEndpoint[Unit, Error, Unit, Any]
  }

  it should "compile with multiple error variants" in {
    sealed trait Error
    case class BadRequest(message: String) extends Error
    case object NotFound extends Error
    case object ReallyNotFound extends Error

    val e = endpoint.post
      .errorOut(statusCode(StatusCode.BadRequest).and(stringBody.map(BadRequest(_))(_.message)))
      .errorOutVariants[Error](
        oneOfVariant(statusCode(StatusCode.NotFound).and(emptyOutputAs(NotFound))),
        oneOfVariant(statusCode(StatusCode.NotFound).and(emptyOutputAs(ReallyNotFound)))
      )

    e: PublicEndpoint[Unit, Error, Unit, Any]
  }

  "oneOfVariant" should "not compile when the type erasure of `T` is different from `T`" in {
    assertDoesNotCompile("""
      case class Wrapper[T](s: T)

      endpoint.post
        .errorOut(
          sttp.tapir.oneOf(
            oneOfVariant(StatusCode.BadRequest, stringBody.map(Wrapper(_))(_.s)),
          )
        )
    """)
  }

  def pairToTuple(input: EndpointInput[?]): Any =
    input match {
      case EndpointInput.Pair(left, right, _, _) => (pairToTuple(left), pairToTuple(right))
      case EndpointIO.Pair(left, right, _, _)    => (pairToTuple(left), pairToTuple(right))
      case EndpointIO.Empty(_, _)                => ()
      case i                                     => i
    }

  "tupling" should "combine two inputs" in {
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

    def extend[I, E, O](e: PublicEndpoint[I, E, O, Any]): PublicEndpoint[(I, String, String), E, O, Any] = e.in(i34)
    val extended1: PublicEndpoint[(String, String, String), Unit, Unit, Any] = extend(endpoint.in(i1))
    val extended2: PublicEndpoint[((String, String), String, String), Unit, Unit, Any] = extend(endpoint.in(i1.and(i2)))

    pairToTuple(extended1.input) shouldBe ((((), i1), (i3, i4)))
    pairToTuple(extended2.input) shouldBe ((((), (i1, i2)), (i3, i4)))
  }

  it should "combine four inputs in two groups, through an extend method (left)" in {
    val i1 = query[String]("q1")
    val i2 = query[String]("q2")
    val i3 = query[String]("q3")
    val i4 = query[String]("q4")
    val i34 = i3.and(i4)

    def extend[I, E, O](e: PublicEndpoint[I, E, O, Any]): PublicEndpoint[(String, String, I), E, O, Any] = e.prependIn(i34)
    val extended1: PublicEndpoint[(String, String, String), Unit, Unit, Any] = extend(endpoint.in(i1))
    val extended2: PublicEndpoint[(String, String, (String, String)), Unit, Unit, Any] = extend(endpoint.in(i1.and(i2)))

    pairToTuple(extended1.input) shouldBe (((i3, i4), ((), i1)))
    pairToTuple(extended2.input) shouldBe (((i3, i4), ((), (i1, i2))))
  }

  val showTestData = List(
    (endpoint.in("p1/p2"), "/p1%2Fp2 -> -/-"),
    (endpoint.name("E1").in("p1"), "[E1] /p1 -> -/-"),
    (endpoint.get.in("p1" / "p2"), "GET /p1 /p2 -> -/-"),
    (endpoint.in("p1" / path[String]("p2") / paths), "/p1 /[p2] /* -> -/-"),
    (
      endpoint.post.in(query[String]("q1")).in(query[Option[Int]]("q2")).in(stringBody).errorOut(stringBody),
      "POST ?q1 ?q2 {body as text/plain (UTF-8)} -> {body as text/plain (UTF-8)}/-"
    ),
    (endpoint.get.in(header[String]("X-header")).out(header[String]("Y-header")), "GET {header X-header} -> -/{header Y-header}"),
    (
      endpoint.get.out(sttp.tapir.oneOf(oneOfVariant(stringBody), oneOfVariant(byteArrayBody))),
      "GET -> -/one of({body as text/plain (UTF-8)}|{body as application/octet-stream})"
    ),
    // same one-of mappings should be flattened to a single clause
    (endpoint.get.out(sttp.tapir.oneOf(oneOfVariant(stringBody), oneOfVariant(stringBody))), "GET -> -/{body as text/plain (UTF-8)}"),
    // nested same one-of mappings should also be flattened
    (
      endpoint.get.out(
        sttp.tapir.oneOf(
          oneOfVariant(byteArrayBody),
          oneOfVariant(sttp.tapir.oneOf(oneOfVariant(byteArrayBody), oneOfVariant(byteArrayBody)))
        )
      ),
      "GET -> -/{body as application/octet-stream}"
    )
  )

  for ((testShowEndpoint, expectedShowResult) <- showTestData) {
    s"show for ${testShowEndpoint.showDetail}" should s"be $expectedShowResult" in {
      testShowEndpoint.show shouldBe expectedShowResult
    }
  }

  val showShortTestData = List(
    endpoint -> "* *",
    endpoint.in("p1") -> "* /p1",
    endpoint.in("p1" / "p2") -> "* /p1/p2",
    endpoint.get.in("p1" / "p2") -> "GET /p1/p2",
    endpoint.post -> "POST *",
    endpoint.get.in("p1").in(paths) -> "GET /p1/*",
    endpoint.get.in("p1" / "p2").name("my endpoint") -> "[my endpoint]"
  )

  for ((testEndpoint, expectedResult) <- showShortTestData) {
    s"short show for ${testEndpoint.showDetail}" should s"be $expectedResult" in {
      testEndpoint.showShort shouldBe expectedResult
    }
  }

  private val pathAllowedCharacters = Rfc3986.PathSegment.mkString

  val showPathTemplateTestData = List(
    (endpoint, "*"),
    (endpoint.in(""), "/"),
    (endpoint.in("p1"), "/p1"),
    (endpoint.in("p1" / "p2"), "/p1/p2"),
    (endpoint.securityIn("p1").in("p2"), "/p1/p2"),
    (endpoint.in("p1" / path[String]), "/p1/{param1}"),
    (endpoint.in("p1" / path[String].name("par")), "/p1/{par}"),
    (endpoint.in("p1" / query[String]("par")), "/p1?par={par}"),
    (endpoint.in("p1" / query[String]("par1") / query[String]("par2")), "/p1?par1={par1}&par2={par2}"),
    (endpoint.in("p1" / path[String].name("par1") / query[String]("par2")), "/p1/{par1}?par2={par2}"),
    (endpoint.in("p1" / auth.apiKey(query[String]("par2"))), "/p1?par2={par2}"),
    (endpoint.in("p2" / path[String]).mapIn(identity(_))(identity(_)), "/p2/{param1}"),
    (endpoint.in("p1/p2"), "/p1%2Fp2"),
    (endpoint.in(pathAllowedCharacters), "/" + pathAllowedCharacters),
    (endpoint.in("p1" / paths), "/p1/*"),
    (endpoint.in("p1").in(queryParams), "/p1?*"),
    (
      endpoint.in("p1" / "p2".schema(_.hidden(true)) / query[String]("par1") / query[String]("par2").schema(_.hidden(true))),
      "/p1?par1={par1}"
    ),
    (endpoint.in("not" / "allowed" / "chars" / "hi?hello"), "/not/allowed/chars/hi%3Fhello")
  )

  for ((testEndpoint, expectedShownPath) <- showPathTemplateTestData) {
    s"showPathTemplate for ${testEndpoint.showDetail}" should s"be $expectedShownPath" in {
      testEndpoint.showPathTemplate() shouldBe expectedShownPath
    }
  }

  "showPathTemplate" should "keep param count in show functions" in {
    val testEndpoint = endpoint.in("p1" / path[String] / query[String]("param"))
    testEndpoint.showPathTemplate(
      showPathParam = (index, _) => s"{par$index}",
      showQueryParam = Some((index, query) => s"${query.name}={par$index}")
    ) shouldBe "/p1/{par1}?param={par2}"
  }

  "showPathTemplate" should "skip query parameters" in {
    val testEndpoint = endpoint.in("p1" / path[String] / query[String]("param"))
    testEndpoint.showPathTemplate(
      showQueryParam = None
    ) shouldBe "/p1/{param1}"
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
    case class Wrapper2(s1: String, s2: String)

    endpoint.in(query[String]("q1")).mapInTo[Wrapper]: PublicEndpoint[Wrapper, Unit, Unit, Any]
    endpoint.in(query[String]("q1")).in(query[String]("q2")).mapInTo[Wrapper2]: PublicEndpoint[Wrapper2, Unit, Unit, Any]
    endpoint.out(stringBody).mapOutTo[Wrapper]: PublicEndpoint[Unit, Unit, Wrapper, Any]
    endpoint.errorOut(stringBody).mapErrorOutTo[Wrapper]: PublicEndpoint[Unit, Wrapper, Unit, Any]
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
      testEndpoint.method shouldBe expectedMethod
    }
  }

  "compile" should "work for endpoint descriptions providing partial server logic using serverLogicForCurrent" in {
    case class User1(x: String, y: Int)
    case class Result(u1: User1, z: Double, a: String)
    val base: PartialServerEndpoint[(String, Int), User1, Unit, String, Unit, Any, Future] = endpoint
      .errorOut(stringBody)
      .securityIn(query[String]("x"))
      .securityIn(query[Int]("y"))
      .serverSecurityLogic { case (x, y) => Future.successful(Right(User1(x, y)): Either[String, User1]) }

    implicit val schemaForResult: Schema[Result] = Schema[Result](SchemaType.SProduct(List.empty), Some(SName.Unit))
    implicit val codec: Codec[String, Result, CodecFormat.TextPlain] = Codec.parsedString(_ => Result(null, 0.0d, ""))

    base
      .in(query[Double]("z"))
      .in(query[String]("a"))
      .out(plainBody[Result])
      .serverLogic { u =>
        { case (z, a) =>
          Future.successful(Right(Result(u, z, a)): Either[String, Result])
        }
      }
  }

  "mapTo" should "properly map between tuples and case classes of arity 1" in {
    // given
    case class Wrapper(i: Int)
    val mapped = query[Int]("q1").mapTo[Wrapper]
    val codec: Codec[List[String], Wrapper, CodecFormat] = mapped.codec

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

  "show" should "provide a compact representation of the endpoint" in {
    endpoint.post
      .in("p1")
      .in(auth.apiKey(query[String]("par2")))
      .in(path[Int]("par3").validate(Validator.min(1)))
      .show shouldBe "POST /p1 /[par3] ?par2 -> -/-"
  }

  "showDetail" should "provide a detailed representation of the endpoint" in {
    endpoint.post
      .in("p1")
      .in(auth.apiKey(query[String]("par2")))
      .in(path[Int]("par3").validate(Validator.min(1)))
      .showDetail shouldBe "Endpoint(securityin: -, in: POST /p1 auth(api key, via ?par2) /[par3](>=1), errout: -, out: -)"
  }
}
