import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import io.circe.parser.parse
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.UriContext
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.generated.{TapirGeneratedEndpoints, TapirGeneratedEndpointsJsonSerdes}
import TapirGeneratedEndpointsJsonSerdes._
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir.generated.TapirGeneratedEndpoints.SubtypeWithoutD3E2.A
import sttp.tapir.generated.TapirGeneratedEndpoints._
import sttp.tapir.server.stub.TapirStubInterpreter

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class JsonRoundtrip extends AnyFreeSpec with Matchers {
  "oneOf without discriminator can be round-tripped by generated serdes" in {
    val route = TapirGeneratedEndpoints.putAdtTest.serverLogic[Future]({
      case foo: SubtypeWithoutD1 =>
        Future successful Right[Unit, ADTWithoutDiscriminator](SubtypeWithoutD1(foo.s + "+SubtypeWithoutD1", foo.i, foo.a))
      case foo: SubtypeWithoutD2 => Future successful Right[Unit, ADTWithoutDiscriminator](SubtypeWithoutD2(foo.a :+ "+SubtypeWithoutD2"))
      case foo: SubtypeWithoutD3 =>
        Future successful Right[Unit, ADTWithoutDiscriminator](
          SubtypeWithoutD3(s = foo.s + "+SubtypeWithoutD3", i = foo.i, e = foo.e, e2 = foo.e2)
        )
    })

    val stub = TapirStubInterpreter(SttpBackendStub.asynchronousFuture)
      .whenServerEndpoint(route)
      .thenRunLogic()
      .backend()

    def normalise(json: String): String = parse(json).toTry.get.noSpacesSortKeys
    locally {
      val reqBody = SubtypeWithoutD1("a string", Some(123), Seq("string 1", "string 2"))
      val reqJsonBody = writeToString(reqBody)
      val respBody = SubtypeWithoutD1("a string+SubtypeWithoutD1", Some(123), Seq("string 1", "string 2"))
      val respJsonBody = writeToString(respBody)
      reqJsonBody shouldEqual """{"s":"a string","i":123,"a":["string 1","string 2"]}"""
      respJsonBody shouldEqual """{"s":"a string+SubtypeWithoutD1","i":123,"a":["string 1","string 2"]}"""
      Await.result(
        sttp.client3.basicRequest
          .put(uri"http://test.com/adt/test")
          .body(reqJsonBody)
          .send(stub)
          .map { resp =>
            resp.code.code shouldEqual 200
            resp.body shouldEqual Right(respJsonBody)
          },
        1.second
      )
    }

    locally {
      val reqBody = SubtypeWithoutD2(Seq("string 1", "string 2"))
      val reqJsonBody = writeToString(reqBody)
      val respBody = SubtypeWithoutD2(Seq("string 1", "string 2", "+SubtypeWithoutD2"))
      val respJsonBody = writeToString(respBody)
      reqJsonBody shouldEqual """{"a":["string 1","string 2"]}"""
      respJsonBody shouldEqual """{"a":["string 1","string 2","+SubtypeWithoutD2"]}"""
      Await.result(
        sttp.client3.basicRequest
          .put(uri"http://test.com/adt/test")
          .body(reqJsonBody)
          .send(stub)
          .map { resp =>
            resp.body shouldEqual Right(respJsonBody)
            resp.code.code shouldEqual 200
          },
        1.second
      )
    }

    locally {
      val reqBody = SubtypeWithoutD3(s = "a string", i = Some(123), e = Some(AnEnum.Foo), e2 = Some(SubtypeWithoutD3E2.A))
      val reqJsonBody = writeToString(reqBody)
      val respBody = SubtypeWithoutD3(s = "a string+SubtypeWithoutD3", i = Some(123), e = Some(AnEnum.Foo), e2 = Some(SubtypeWithoutD3E2.A))
      val respJsonBody = writeToString(respBody)
      reqJsonBody shouldEqual """{"s":"a string","i":123,"e2":"A","e":"Foo"}"""
      respJsonBody shouldEqual """{"s":"a string+SubtypeWithoutD3","i":123,"e2":"A","e":"Foo"}"""
      Await.result(
        sttp.client3.basicRequest
          .put(uri"http://test.com/adt/test")
          .body(reqJsonBody)
          .send(stub)
          .map { resp =>
            resp.body shouldEqual Right(respJsonBody)
            resp.code.code shouldEqual 200
          },
        1.second
      )
    }
  }
  "oneOf with discriminator can be round-tripped by generated serdes" in {
    val route = TapirGeneratedEndpoints.postAdtTest.serverLogic[Future]({
      case foo: SubtypeWithD1 => Future successful Right[Unit, ADTWithDiscriminator](SubtypeWithD1(foo.s + "+SubtypeWithD1", foo.i, foo.d))
      case foo: SubtypeWithD2 => Future successful Right[Unit, ADTWithDiscriminator](SubtypeWithD2(foo.s + "+SubtypeWithD2", foo.a))
    })

    val stub = TapirStubInterpreter(SttpBackendStub.asynchronousFuture)
      .whenServerEndpoint(route)
      .thenRunLogic()
      .backend()

    def normalise(json: String): String = parse(json).toTry.get.noSpacesSortKeys

    locally {
      val reqBody: ADTWithDiscriminatorNoMapping = SubtypeWithD1("a string", Some(123), Some(23.4))
      val reqJsonBody = writeToString(reqBody)
      val respBody: ADTWithDiscriminator = SubtypeWithD1("a string+SubtypeWithD1", Some(123), Some(23.4))
      val respJsonBody = writeToString(respBody)
      reqJsonBody shouldEqual """{"type":"SubtypeWithD1","s":"a string","i":123,"d":23.4}"""
      respJsonBody shouldEqual """{"type":"SubA","s":"a string+SubtypeWithD1","i":123,"d":23.4}"""
      Await.result(
        sttp.client3.basicRequest
          .post(uri"http://test.com/adt/test")
          .body(reqJsonBody)
          .send(stub)
          .map { resp =>
            resp.code.code shouldEqual 200
            resp.body shouldEqual Right(respJsonBody)
          },
        1.second
      )
    }

    locally {
      val reqBody: ADTWithDiscriminatorNoMapping = SubtypeWithD2("a string", Some(Seq("string 1", "string 2")))
      val reqJsonBody = writeToString(reqBody)
      val respBody: ADTWithDiscriminator = SubtypeWithD2("a string+SubtypeWithD2", Some(Seq("string 1", "string 2")))
      val respJsonBody = writeToString(respBody)
      reqJsonBody shouldEqual """{"type":"SubtypeWithD2","s":"a string","a":["string 1","string 2"]}"""
      respJsonBody shouldEqual """{"type":"SubB","s":"a string+SubtypeWithD2","a":["string 1","string 2"]}"""
      Await.result(
        sttp.client3.basicRequest
          .post(uri"http://test.com/adt/test")
          .body(reqJsonBody)
          .send(stub)
          .map { resp =>
            resp.code.code shouldEqual 200
            resp.body shouldEqual Right(respJsonBody)
          },
        1.second
      )
    }

  }
  "oneOf Option" in {
    var returnSome: Boolean = false
    val someResponse = AnEnum.Foo
    val route = TapirGeneratedEndpoints.getOneofOptionTest.serverLogic[Future]({ _: Unit =>
      Future successful Right[Unit, Option[AnEnum]](Option.when(returnSome)(someResponse))
    })
    val stub = TapirStubInterpreter(SttpBackendStub.asynchronousFuture)
      .whenServerEndpoint(route)
      .thenRunLogic()
      .backend()
    Await.result(
      sttp.client3.basicRequest
        .get(uri"http://test.com/oneof/option/test")
        .send(stub)
        .map { resp =>
          resp.code.code shouldEqual 204
          resp.body shouldEqual Right("")
        },
      1.second
    )
    returnSome = true
    Await.result(
      sttp.client3.basicRequest
        .get(uri"http://test.com/oneof/option/test")
        .send(stub)
        .map { resp =>
          resp.code.code shouldEqual 200
          resp.body shouldEqual Right(s"\"Foo\"")
        },
      1.second
    )
  }
//  "content-type negotiation" in {
//    val route = TMP.getContentTypeNegotiation.serverLogic[Future]({ _: Unit =>
//      Future successful Right[Unit, PekkoStreams.BinaryStream](???)
//    })
//    val stub = TapirStubInterpreter(SttpBackendStub.asynchronousFuture)
//      .whenServerEndpoint(route)
//      .thenRunLogic()
//      .backend()
//    Await.result(
//      sttp.client3.basicRequest
//        .get(uri"http://test.com/oneof/option/test")
//        .send(stub)
//        .map { resp =>
//          resp.code.code shouldEqual 204
//          resp.body shouldEqual Right("")
//        },
//      1.second
//    )
//
//  }
}

//object TMP {
//  import sttp.tapir._
//
//  type GetContentTypeNegotiationEndpoint =
//    Endpoint[Unit, Unit, Unit, sttp.capabilities.pekko.PekkoStreams.BinaryStream, sttp.capabilities.pekko.PekkoStreams]
//  lazy val getContentTypeNegotiation: GetContentTypeNegotiationEndpoint =
//    endpoint.get
//      .in(("content-type" / "negotiation"))
//      .out(
//        oneOf[sttp.capabilities.pekko.PekkoStreams.BinaryStream](
//          oneOfVariant[sttp.capabilities.pekko.PekkoStreams.BinaryStream](
//            sttp.model.StatusCode(400),
//            streamBody(sttp.capabilities.pekko.PekkoStreams)(
//              Schema.binary[SomeBinaryType],
//              `application/vnd.openxmlformats-officedocument.spreadsheetml.sheetCodecFormat`()
//            ).description("Download the upload")
//          ),
//          oneOfVariant[sttp.capabilities.pekko.PekkoStreams.BinaryStream](
//            sttp.model.StatusCode(400),
//            streamBody(sttp.capabilities.pekko.PekkoStreams)(
//              Schema.binary[SomeBinaryType],
//              CodecFormat.OctetStream()
//            ).description("Download the upload")
//          )
//        )
//      )
//
//  type PostContentTypeNegotiationEndpoint =
//    Endpoint[Unit, sttp.capabilities.pekko.PekkoStreams.BinaryStream, Unit, Unit, sttp.capabilities.pekko.PekkoStreams]
//  lazy val postContentTypeNegotiation: PostContentTypeNegotiationEndpoint =
//    endpoint.post
//      .in(("content-type" / "negotiation"))
//      .in(
//        streamBody(sttp.capabilities.pekko.PekkoStreams)(
//          Schema.binary[SomeBinaryType],
//          `application/vnd.openxmlformats-officedocument.spreadsheetml.sheetCodecFormat`()
//        )
//      )
//
//}
