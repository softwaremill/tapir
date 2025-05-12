import io.circe.parser.parse
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.UriContext
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.generated.TapirGeneratedEndpoints.ObjectWithInlineEnum2InlineEnum.bar2
import sttp.tapir.generated.TapirGeneratedEndpoints.ObjectWithInlineEnumInlineEnum.foo3
import sttp.tapir.generated.{TapirGeneratedEndpoints, TapirGeneratedEndpointsJsonSerdes}
import sttp.tapir.generated.TapirGeneratedEndpoints._
import sttp.tapir.server.stub.TapirStubInterpreter

import java.util.{Base64, UUID}
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
        Future successful Right[Unit, ADTWithoutDiscriminator](SubtypeWithoutD3(foo.s + "+SubtypeWithoutD3", foo.i, foo.e))
    })

    val stub = TapirStubInterpreter(SttpBackendStub.asynchronousFuture)
      .whenServerEndpoint(route)
      .thenRunLogic()
      .backend()

    def normalise(json: String): String = parse(json).toTry.get.noSpacesSortKeys
    locally {
      val reqBody = SubtypeWithoutD1("a string", Some(123), Seq("string 1", "string 2"))
      val reqJsonBody = TapirGeneratedEndpointsJsonSerdes.aDTWithoutDiscriminatorJsonEncoder(reqBody).noSpacesSortKeys
      val respBody = SubtypeWithoutD1("a string+SubtypeWithoutD1", Some(123), Seq("string 1", "string 2"))
      val respJsonBody = TapirGeneratedEndpointsJsonSerdes.aDTWithoutDiscriminatorJsonEncoder(respBody).noSpacesSortKeys
      reqJsonBody shouldEqual """{"a":["string 1","string 2"],"absent":null,"i":123,"s":"a string"}"""
      respJsonBody shouldEqual """{"a":["string 1","string 2"],"absent":null,"i":123,"s":"a string+SubtypeWithoutD1"}"""
      Await.result(
        sttp.client3.basicRequest
          .put(uri"http://test.com/adt/test")
          .body(reqJsonBody)
          .send(stub)
          .map { resp =>
            resp.code.code shouldEqual 200
            resp.body.map(normalise) shouldEqual Right(respJsonBody)
          },
        1.second
      )
    }

    locally {
      val reqBody = SubtypeWithoutD2(Seq("string 1", "string 2"))
      val reqJsonBody = TapirGeneratedEndpointsJsonSerdes.aDTWithoutDiscriminatorJsonEncoder(reqBody).noSpacesSortKeys
      val respBody = SubtypeWithoutD2(Seq("string 1", "string 2", "+SubtypeWithoutD2"))
      val respJsonBody = TapirGeneratedEndpointsJsonSerdes.aDTWithoutDiscriminatorJsonEncoder(respBody).noSpacesSortKeys
      reqJsonBody shouldEqual """{"a":["string 1","string 2"],"absent":null}"""
      respJsonBody shouldEqual """{"a":["string 1","string 2","+SubtypeWithoutD2"],"absent":null}"""
      Await.result(
        sttp.client3.basicRequest
          .put(uri"http://test.com/adt/test")
          .body(reqJsonBody)
          .send(stub)
          .map { resp =>
            resp.body.map(normalise) shouldEqual Right(respJsonBody)
            resp.code.code shouldEqual 200
          },
        1.second
      )
    }

    locally {
      val reqBody = SubtypeWithoutD3("a string", Some(123), Some(AnEnum.Foo))
      val reqJsonBody = TapirGeneratedEndpointsJsonSerdes.aDTWithoutDiscriminatorJsonEncoder(reqBody).noSpacesSortKeys
      val respBody = SubtypeWithoutD3("a string+SubtypeWithoutD3", Some(123), Some(AnEnum.Foo))
      val respJsonBody = TapirGeneratedEndpointsJsonSerdes.aDTWithoutDiscriminatorJsonEncoder(respBody).noSpacesSortKeys
      reqJsonBody shouldEqual """{"absent":null,"e":"Foo","i":123,"s":"a string"}"""
      respJsonBody shouldEqual """{"absent":null,"e":"Foo","i":123,"s":"a string+SubtypeWithoutD3"}"""
      Await.result(
        sttp.client3.basicRequest
          .put(uri"http://test.com/adt/test")
          .body(reqJsonBody)
          .send(stub)
          .map { resp =>
            resp.body.map(normalise) shouldEqual Right(respJsonBody)
            resp.code.code shouldEqual 200
          },
        1.second
      )
    }
  }
  "oneOf with discriminator can be round-tripped by generated serdes" in {
    val route = TapirGeneratedEndpoints.postAdtTest
      .serverSecurityLogicSuccess(_ => Future.successful(()))
      .serverLogic(_ => {
        case foo: SubtypeWithD1 =>
          val encoded = (new String(foo.s, "utf-8") ++ "+SubtypeWithD1").getBytes("utf-8")
          Future successful Right[Unit, ADTWithDiscriminator](SubtypeWithD1(encoded, foo.i, foo.d))
        case foo: SubtypeWithD2 => Future successful Right[Unit, ADTWithDiscriminator](SubtypeWithD2(foo.s + "+SubtypeWithD2", foo.a))
      })

    val stub = TapirStubInterpreter(SttpBackendStub.asynchronousFuture)
      .whenServerEndpoint(route)
      .thenRunLogic()
      .backend()

    def normalise(json: String): String = parse(json).toTry.get.noSpacesSortKeys

    locally {
      val stringBytes = "a string".getBytes("utf-8")
      val reqBody = SubtypeWithD1(stringBytes, Some(123), Some(23.4))
      val reqJsonBody = TapirGeneratedEndpointsJsonSerdes.aDTWithDiscriminatorNoMappingJsonEncoder(reqBody).noSpacesSortKeys
      val stringBytes2 = "a string+SubtypeWithD1".getBytes("utf-8")
      val respBody = SubtypeWithD1(stringBytes2, Some(123), Some(23.4))
      val respJsonBody = TapirGeneratedEndpointsJsonSerdes.aDTWithDiscriminatorJsonEncoder(respBody).noSpacesSortKeys
      reqJsonBody shouldEqual """{"d":23.4,"i":123,"noMapType":"SubtypeWithD1","s":"YSBzdHJpbmc="}"""
      respJsonBody shouldEqual """{"d":23.4,"i":123,"s":"YSBzdHJpbmcrU3VidHlwZVdpdGhEMQ==","type":"SubA"}"""
      Await.result(
        sttp.client3.basicRequest
          .post(uri"http://test.com/adt/test")
          .header("api_key", "the key")
          .body(reqJsonBody)
          .send(stub)
          .map { resp =>
            resp.code.code shouldEqual 200
            resp.body.map(normalise) shouldEqual Right(respJsonBody)
          },
        1.second
      )
    }

    locally {
      val reqBody = SubtypeWithD2("a string", Some(Seq("string 1", "string 2")))
      val reqJsonBody = TapirGeneratedEndpointsJsonSerdes.aDTWithDiscriminatorNoMappingJsonEncoder(reqBody).noSpacesSortKeys
      val respBody = SubtypeWithD2("a string+SubtypeWithD2", Some(Seq("string 1", "string 2")))
      val respJsonBody = TapirGeneratedEndpointsJsonSerdes.aDTWithDiscriminatorJsonEncoder(respBody).noSpacesSortKeys
      reqJsonBody shouldEqual """{"a":["string 1","string 2"],"noMapType":"SubtypeWithD2","s":"a string"}"""
      respJsonBody shouldEqual """{"a":["string 1","string 2"],"s":"a string+SubtypeWithD2","type":"SubB"}"""
      Await.result(
        sttp.client3.basicRequest
          .post(uri"http://test.com/adt/test")
          .header("api_key", "the key")
          .body(reqJsonBody)
          .send(stub)
          .map { resp =>
            resp.code.code shouldEqual 200
            resp.body.map(normalise) shouldEqual Right(respJsonBody)
          },
        1.second
      )
    }

  }

  "enum query param support" in {
    var lastValues: (
        PostInlineEnumTestQueryEnum,
        Option[PostInlineEnumTestQueryOptEnum],
        List[PostInlineEnumTestQuerySeqEnum],
        Option[List[PostInlineEnumTestQueryOptSeqEnum]],
        ObjectWithInlineEnum
    ) = null
    val route = TapirGeneratedEndpoints.postInlineEnumTest.serverLogic[Future]({ case (a, b, c, d, e) =>
      lastValues = (a, b, c, d, e)
      Future successful Right[Unit, Unit](())
    })

    val stub = TapirStubInterpreter(SttpBackendStub.asynchronousFuture)
      .whenServerEndpoint(route)
      .thenRunLogic()
      .backend()

    locally {
      val id = UUID.randomUUID()
      val reqBody = ObjectWithInlineEnum(id, ObjectWithInlineEnumInlineEnum.foo3)
      val reqJsonBody = TapirGeneratedEndpointsJsonSerdes.objectWithInlineEnumJsonEncoder(reqBody).noSpacesSortKeys
      reqJsonBody shouldEqual s"""{"id":"$id","inlineEnum":"foo3"}"""
      Await.result(
        sttp.client3.basicRequest
          .post(
            uri"http://test.com/inline/enum/test?query-enum=bar1&query-opt-enum=bar2&query-seq-enum=baz1,baz2&query-opt-seq-enum=baz1,baz2"
          )
          .body(reqJsonBody)
          .send(stub)
          .map { resp =>
            resp.code.code shouldEqual 204
            resp.body shouldEqual Right("")
          },
        1.second
      )
      val (a, b, c, d, e) = lastValues
      a shouldEqual PostInlineEnumTestQueryEnum.bar1
      b shouldEqual Some(PostInlineEnumTestQueryOptEnum.bar2)
      c shouldEqual Seq(PostInlineEnumTestQuerySeqEnum.baz1, PostInlineEnumTestQuerySeqEnum.baz2)
      d shouldEqual Some(Seq(PostInlineEnumTestQueryOptSeqEnum.baz1, PostInlineEnumTestQueryOptSeqEnum.baz2))
      e shouldEqual reqBody
    }

    locally {
      val id = UUID.randomUUID()
      val reqBody = ObjectWithInlineEnum(id, ObjectWithInlineEnumInlineEnum.foo3)
      val reqJsonBody = TapirGeneratedEndpointsJsonSerdes.objectWithInlineEnumJsonEncoder(reqBody).noSpacesSortKeys
      reqJsonBody shouldEqual s"""{"id":"$id","inlineEnum":"foo3"}"""
      Await.result(
        sttp.client3.basicRequest
          .post(uri"http://test.com/inline/enum/test?query-enum=bar1&query-seq-enum=baz1,baz2")
          .body(reqJsonBody)
          .send(stub)
          .map { resp =>
            resp.code.code shouldEqual 204
            resp.body shouldEqual Right("")
          },
        1.second
      )
      val (a, b, c, d, e) = lastValues
      a shouldEqual PostInlineEnumTestQueryEnum.bar1
      b shouldEqual None
      c shouldEqual Seq(PostInlineEnumTestQuerySeqEnum.baz1, PostInlineEnumTestQuerySeqEnum.baz2)
      d shouldEqual None
      e shouldEqual reqBody
    }
  }

  "oneOf Option" in {
    var returnVariant: Int = 0
    val someResponse1 = ObjectWithInlineEnum(UUID.randomUUID(), foo3)
    val someResponse2 = ObjectWithInlineEnum2(bar2)
    def responseVariant = returnVariant match {
      case 0 => None
      case 1 => Some(someResponse1)
      case 2 => Some(someResponse2)
    }
    val route = TapirGeneratedEndpoints.getOneofOptionTest
      .serverSecurityLogicSuccess[Unit, Future](_ => Future.successful(()))
      .serverLogic({ _ => _: Unit =>
        Future successful Right[Unit, (Option[AnyObjectWithInlineEnum], Option[String])](responseVariant -> Some("ok"))
      })
    val stub = TapirStubInterpreter(SttpBackendStub.asynchronousFuture)
      .whenServerEndpoint(route)
      .thenRunLogic()
      .backend()
    Await.result(
      sttp.client3.basicRequest
        .get(uri"http://test.com/oneof/option/test")
        .header("Authorization", "Bearer some.jwt.probably")
        .send(stub)
        .map { resp =>
          resp.code.code shouldEqual 204
          resp.body shouldEqual Right("")
        },
      1.second
    )
    returnVariant = 1
    Await.result(
      sttp.client3.basicRequest
        .get(uri"http://test.com/oneof/option/test")
        .header("Authorization", "Bearer some.jwt.probably")
        .send(stub)
        .map { resp =>
          resp.code.code shouldEqual 200
          resp.body shouldEqual Right(s"""{"id":"${someResponse1.id}","inlineEnum":"foo3"}""")
        },
      1.second
    )
    returnVariant = 2
    Await.result(
      sttp.client3.basicRequest
        .get(uri"http://test.com/oneof/option/test")
        .header("Authorization", "Bearer some.jwt.probably")
        .send(stub)
        .map { resp =>
          resp.code.code shouldEqual 201
          resp.body shouldEqual Right(s"""{"inlineEnum":"bar2"}""")
        },
      1.second
    )

  }
  "multiple security declarations" in {
    val uuids = (1 to 3).map(_ => UUID.randomUUID())
    val route = TapirGeneratedEndpoints.putOptionalTest
      .serverSecurityLogicSuccess[Int, Future] {
        case _: Api_keySecurityIn            => Future.successful(0)
        case _: Api_key_and_BearerSecurityIn => Future.successful(1)
        case _: BearerSecurityIn             => Future.successful(2)
      }
      .serverLogic({ i => _ =>
        Future successful Right(NotNullableThingy(uuids(i)))
      })
    val stub = TapirStubInterpreter(SttpBackendStub.asynchronousFuture)
      .whenServerEndpoint(route)
      .thenRunLogic()
      .backend()

    Await.result(
      sttp.client3.basicRequest
        .put(uri"http://test.com/optional/test")
        .header("api_key", "an api key!!")
        .send(stub)
        .map { resp =>
          resp.code.code shouldEqual 200
          resp.body shouldEqual Right(s"""{"uuid":"${uuids(0)}"}""")
        },
      1.second
    )
    Await.result(
      sttp.client3.basicRequest
        .put(uri"http://test.com/optional/test")
        .header("Authorization", "Bearer some.jwt.probably")
        .header("api_key", "an api key!!")
        .send(stub)
        .map { resp =>
          resp.code.code shouldEqual 200
          resp.body shouldEqual Right(s"""{"uuid":"${uuids(1)}"}""")
        },
      1.second
    )
    Await.result(
      sttp.client3.basicRequest
        .put(uri"http://test.com/optional/test")
        .header("Authorization", "Bearer some.jwt.probably")
        .send(stub)
        .map { resp =>
          resp.code.code shouldEqual 200
          resp.body shouldEqual Right(s"""{"uuid":"${uuids(2)}"}""")
        },
      1.second
    )
  }
  "security prefix" in {
    locally {
      val route = TapirGeneratedEndpoints.getSecurityGroupSecurityGroupName
        .serverSecurityLogic[Unit, Future] { case (pathPrefix, bearerToken) =>
          if (bearerToken.startsWith(pathPrefix)) Future.successful(Right())
          else Future.successful(Left(()))
        }
        .serverLogic({ _ => _ => Future successful Right(()) })
      val stub = TapirStubInterpreter(SttpBackendStub.asynchronousFuture)
        .whenServerEndpoint(route)
        .thenRunLogic()
        .backend()

      Await.result(
        sttp.client3.basicRequest
          .get(uri"http://test.com/security-group/foo")
          .header("Authorization", "Bearer foot")
          .send(stub)
          .map { resp =>
            resp.code.code shouldEqual 204
          },
        1.second
      )
      Await.result(
        sttp.client3.basicRequest
          .get(uri"http://test.com/security-group/foot")
          .header("Authorization", "Bearer foo")
          .send(stub)
          .map { resp =>
            resp.code.code shouldEqual 400
          },
        1.second
      )
    }
    locally {
      val route = TapirGeneratedEndpoints.getSecurityGroupSecurityGroupNameMorePath
        .serverSecurityLogic[Unit, Future] { case (pathPrefix, bearerToken) =>
          if (bearerToken.startsWith(pathPrefix)) Future.successful(Right())
          else Future.successful(Left(()))
        }
        .serverLogic({ _ => _ => Future successful Right(()) })
      val stub = TapirStubInterpreter(SttpBackendStub.asynchronousFuture)
        .whenServerEndpoint(route)
        .thenRunLogic()
        .backend()

      Await.result(
        sttp.client3.basicRequest
          .get(uri"http://test.com/security-group/foo/more-path")
          .header("Authorization", "Bearer foot")
          .send(stub)
          .map { resp =>
            resp.code.code shouldEqual 204
          },
        1.second
      )
      Await.result(
        sttp.client3.basicRequest
          .get(uri"http://test.com/security-group/foot/more-path")
          .header("Authorization", "Bearer foo")
          .send(stub)
          .map { resp =>
            resp.code.code shouldEqual 400
          },
        1.second
      )
    }
  }
}
