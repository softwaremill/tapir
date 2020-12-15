package sttp.tapir.annotations

import java.nio.charset.StandardCharsets

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.{Cookie => ModelCookie, Header => ModelHeader, QueryParams => ModelQueryParams}
import sttp.tapir._
import sttp.tapir.annotations._
import sttp.tapir.model.UsernamePassword
import sttp.tapir.EndpointIO._
import sttp.tapir.EndpointInput._
import sttp.tapir.EndpointInput.Auth._
import sttp.tapir.RawBodyType.StringBody

object JsonCodecs {
  implicit val stringJsonCodec = new JsonCodecMock[String]
  implicit val booleanJsonCodec = new JsonCodecMock[Boolean]
}

final case class TapirRequestTest1(
    @query
    field1: Int,
    @query("another-field-name")
    field2: String,
    @cookie
    cookie: Boolean,
    @cookie("another-cookie-name")
    namedCookie: Boolean,
    @header
    header: Long,
    @header("another-header-name")
    namedHeader: Int,
    @jsonbody
    body: String
)

object TapirRequestTest1 {
  import JsonCodecs._
}

final case class TapirRequestTest2(
    @body(StringBody(StandardCharsets.UTF_8), CodecFormat.Json())
    body: Boolean
)

object TapirRequestTest2 {
  import JsonCodecs._
}

final case class TapirRequestTest3(
    @params
    params: ModelQueryParams,
    @headers
    headers: List[ModelHeader],
    @cookies
    cookies: List[ModelCookie]
)

final case class TapirRequestTest4(
    @apikey @query
    param1: Int,
    @basic
    basicAuth: UsernamePassword,
    @bearer
    token: String
)

final case class TapirRequestTest5(
    @query
    @description("field-description")
    field1: Int,
    @cookie
    @deprecated
    cookie: Boolean
)

@endpointInput("some/{field5}/path/{field2}")
final case class TapirRequestTest6(
    @query
    field1: Int,
    @path
    @description("path-description")
    field2: Boolean,
    @query
    field3: Long,
    @query
    field4: String,
    @path
    @apikey
    field5: Int
)

class EndpointInputMacroTest extends AnyFlatSpec with Matchers with Tapir {

  "@endpointInput" should "derive correct input for @query, @cookie, @header" in {
    import JsonCodecs._

    val expectedInput = query[Int]("field1")
      .and(query[String]("another-field-name"))
      .and(cookie[Boolean]("cookie"))
      .and(cookie[Boolean]("another-cookie-name"))
      .and(header[Long]("header"))
      .and(header[Int]("another-header-name"))
      .and(anyJsonBody[String])
      .mapTo(TapirRequestTest1.apply _)

    compareInputs(deriveEndpointInput[TapirRequestTest1], expectedInput) shouldBe true
  }

  it should "derive correct input for dealised bodies" in {
    import JsonCodecs._

    val expectedInput = anyJsonBody[Boolean].mapTo(TapirRequestTest2.apply _)

    compareInputs(deriveEndpointInput[TapirRequestTest2], expectedInput) shouldBe true
  }

  it should "derive correct input for @queries, @headers, @cookies" in {
    val expectedInput = queryParams.and(headers).and(cookies).mapTo(TapirRequestTest3.apply _)

    compareInputs(deriveEndpointInput[TapirRequestTest3], expectedInput) shouldBe true
  }

  it should "derive correct input for auth annotations" in {
    val expectedInput = TapirAuth
      .apiKey(query[Int]("param1"))
      .and(TapirAuth.basic[UsernamePassword])
      .and(TapirAuth.bearer[String])
      .mapTo(TapirRequestTest4.apply _)

    compareInputs(deriveEndpointInput[TapirRequestTest4], expectedInput) shouldBe true
  }

  it should "derive input with descriptions" in {
    val expectedInput = query[Int]("field1")
      .description("field-description")
      .and(cookie[Boolean]("cookie").deprecated)
      .mapTo(TapirRequestTest5.apply _)

    compareInputs(deriveEndpointInput[TapirRequestTest5], expectedInput) shouldBe true
  }

  it should "derive input with paths" in {
    val derivedInput = deriveEndpointInput[TapirRequestTest6]

    val expectedInput = "some"
      .and(TapirAuth.apiKey(path[Int]("field5")))
      .and("path")
      .and(path[Boolean]("field2").description("path-description"))
      .and(query[Int]("field1"))
      .and(query[Long]("field3"))
      .and(query[String]("field4"))
      .mapTo { (field5, field2, field1, field3, field4) =>
        TapirRequestTest6(field1, field2, field3, field4, field5)
      }

    compareInputs(derivedInput, expectedInput) shouldBe true
  }

  it should "not compile if there is field without annotation" in {
    assertDoesNotCompile("""
      final case class Test(
        @header
        h: Int,
        @query
        q: Boolean,
        i: Long
      )

      object Test {
        deriveEndpointInput[Test]
      }
    """)
  }

  it should "not compile if there are two body annotations" in {
    assertDoesNotCompile("""
      final case class Test(
        @jsonbody
        body1: Int,
        @xmlbody
        body2: Long
      )

      object Test {
        deriveEndpointInput[Test]
      }
    """)
  }

  it should "not compile for alone @apikey" in {
    assertDoesNotCompile("""
      final case class Test(
        @apikey
        query: Int
      )

      object Test {
        deriveEndpointInput[Test]
      }
    """)
  }

  it should "not compile for body without JSON codec" in {
    assertDoesNotCompile("""
      final case class Test(
        @jsonbody
        query: Int
      )

      object Test {
        deriveEndpointInput[Test]
      }
    """)
  }

  it should "not compile when not all paths are captured in case class" in {
    assertDoesNotCompile("""
      final case class Test(
        @query
        field1: Int,
        @path
        field2: String
      )
      object Test {
        val input = deriveEndpointInput[Test]("/asdf/{field2}/{field3}")
      }
    """)
  }

  it should "not compile when not all paths are captured in path" in {
    assertDoesNotCompile("""
      final case class Test(
        @query
        field1: Int,
        @path
        field2: String,
        @path
        field3: Long
      )
      object Test {
        val input = deriveEndpointInput[Test]("/asdf/{field2}")
      }
    """)
  }

  it should "not compile when path contains duplicated variable" in {
    assertDoesNotCompile("""
      final case class Test(
        @query
        field1: Int,
        @path
        field2: String
      )
      object Test {
        val input = deriveEndpointInput[Test]("/asdf/{field2}/{field2}")
      }
    """)
  }

  def compareInputs(left: EndpointInput[_], right: EndpointInput[_]): Boolean =
    (left, right) match {
      case (EndpointInput.Pair(left1, right1, _, _), EndpointInput.Pair(left2, right2, _, _)) =>
        compareInputs(left1, left2) && compareInputs(right1, right2)
      case (EndpointInput.MappedPair(input1, _), EndpointInput.MappedPair(input2, _)) =>
        compareInputs(input1, input2)
      case (FixedMethod(m1, _, info1), FixedMethod(m2, _, info2)) =>
        m1 == m2 && info1 == info2
      case (FixedPath(s1, _, info1), FixedPath(s2, _, info2)) =>
        s1 == s2 && info1 == info2
      case (PathCapture(name1, _, info1), PathCapture(name2, _, info2)) =>
        name1 == name2 && info1 == info2
      case (PathsCapture(_, info1), PathsCapture(_, info2)) =>
        info1 == info2
      case (Query(name1, _, info1), Query(name2, _, info2)) =>
        name1 == name2 && info1 == info2
      case (QueryParams(_, info1), QueryParams(_, info2)) =>
        info1 == info2
      case (Cookie(name1, _, info1), Cookie(name2, _, info2)) =>
        name1 == name2 && info1 == info2
      case (ExtractFromRequest(_, info1), ExtractFromRequest(_, info2)) =>
        info1 == info2
      case (ApiKey(input1), ApiKey(input2)) =>
        compareInputs(input1, input2)
      case (Http(scheme1, input1), Http(scheme2, input2)) =>
        scheme1 == scheme2 && compareInputs(input1, input2)
      case (Body(bodyType1, _, info1), Body(bodyType2, _, info2)) =>
        bodyType1 == bodyType2 && info1 == info2
      case (FixedHeader(h1, _, info1), FixedHeader(h2, _, info2)) =>
        h1 == h2 && info1 == info2
      case (Header(name1, _, info1), Header(name2, _, info2)) =>
        name1 == name2 && info1 == info2
      case (Headers(_, info1), Headers(_, info2)) =>
        info1 == info2
      case (EndpointIO.MappedPair(io1, _), EndpointIO.MappedPair(io2, _)) =>
        io1 == io2
      case (EndpointIO.Pair(left1, right1, _, _), EndpointIO.Pair(left2, right2, _, _)) =>
        compareInputs(left1, left2) && compareInputs(right1, right2)
      case (_, _) =>
        false
    }
}

class JsonCodecMock[T] extends Codec[String, T, CodecFormat.Json] {

  override def rawDecode(l: String): DecodeResult[T] = ???

  override def encode(h: T): String = ???

  override def schema: Schema[T] = ???

  override def format: CodecFormat.Json = ???
}
