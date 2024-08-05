import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import io.circe.parser.parse
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.UriContext
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.generated.{TapirGeneratedEndpoints, TapirGeneratedEndpointsJsonSerdes}
import sttp.tapir.generated.TapirGeneratedEndpoints.*
import sttp.tapir.generated.TapirGeneratedEndpointsSchemas.*
import TapirGeneratedEndpointsJsonSerdes._
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
        Future successful Right[Unit, ADTWithoutDiscriminator](SubtypeWithoutD3(foo.s + "+SubtypeWithoutD3", foo.i, foo.e))
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
            resp.code.code === 200
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
            resp.code.code === 200
          },
        1.second
      )
    }

    locally {
      val reqBody = SubtypeWithoutD3("a string", Some(123), Some(AnEnum.Foo))
      val reqJsonBody = writeToString(reqBody)
      val respBody = SubtypeWithoutD3("a string+SubtypeWithoutD3", Some(123), Some(AnEnum.Foo))
      val respJsonBody = writeToString(respBody)
      reqJsonBody shouldEqual """{"s":"a string","i":123,"e":"Foo"}"""
      respJsonBody shouldEqual """{"s":"a string+SubtypeWithoutD3","i":123,"e":"Foo"}"""
      Await.result(
        sttp.client3.basicRequest
          .put(uri"http://test.com/adt/test")
          .body(reqJsonBody)
          .send(stub)
          .map { resp =>
            resp.body shouldEqual Right(respJsonBody)
            resp.code.code === 200
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
            resp.code.code === 200
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
            resp.code.code === 200
            resp.body shouldEqual Right(respJsonBody)
          },
        1.second
      )
    }

  }
}
