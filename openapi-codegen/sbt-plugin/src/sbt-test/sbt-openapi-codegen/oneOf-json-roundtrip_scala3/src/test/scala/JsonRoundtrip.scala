import io.circe.parser.parse
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.UriContext
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.generated.{TapirGeneratedEndpoints, TapirGeneratedEndpointsJsonSerdes}
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
        Future successful Right[Unit, ADTWithoutDiscriminator](SubtypeWithoutD3(foo.s + "+SubtypeWithoutD3", foo.i, foo.d))
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
      reqJsonBody shouldEqual """{"a":["string 1","string 2"],"i":123,"s":"a string"}"""
      respJsonBody shouldEqual """{"a":["string 1","string 2"],"i":123,"s":"a string+SubtypeWithoutD1"}"""
      Await.result(
        sttp.client3.basicRequest
          .put(uri"http://test.com/adt/test")
          .body(reqJsonBody)
          .send(stub)
          .map { resp =>
            resp.code.code === 200
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
      reqJsonBody shouldEqual """{"a":["string 1","string 2"]}"""
      respJsonBody shouldEqual """{"a":["string 1","string 2","+SubtypeWithoutD2"]}"""
      Await.result(
        sttp.client3.basicRequest
          .put(uri"http://test.com/adt/test")
          .body(reqJsonBody)
          .send(stub)
          .map { resp =>
            resp.body.map(normalise) shouldEqual Right(respJsonBody)
            resp.code.code === 200
          },
        1.second
      )
    }

    locally {
      val reqBody = SubtypeWithoutD3("a string", Some(123), Some(23.4))
      val reqJsonBody = TapirGeneratedEndpointsJsonSerdes.aDTWithoutDiscriminatorJsonEncoder(reqBody).noSpacesSortKeys
      val respBody = SubtypeWithoutD3("a string+SubtypeWithoutD3", Some(123), Some(23.4))
      val respJsonBody = TapirGeneratedEndpointsJsonSerdes.aDTWithoutDiscriminatorJsonEncoder(respBody).noSpacesSortKeys
      reqJsonBody shouldEqual """{"d":23.4,"i":123,"s":"a string"}"""
      respJsonBody shouldEqual """{"d":23.4,"i":123,"s":"a string+SubtypeWithoutD3"}"""
      Await.result(
        sttp.client3.basicRequest
          .put(uri"http://test.com/adt/test")
          .body(reqJsonBody)
          .send(stub)
          .map { resp =>
            resp.body.map(normalise) shouldEqual Right(respJsonBody)
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
      val reqBody = SubtypeWithD1("a string", Some(123), Some(23.4))
      val reqJsonBody = TapirGeneratedEndpointsJsonSerdes.aDTWithDiscriminatorNoMappingJsonEncoder(reqBody).noSpacesSortKeys
      val respBody = SubtypeWithD1("a string+SubtypeWithD1", Some(123), Some(23.4))
      val respJsonBody = TapirGeneratedEndpointsJsonSerdes.aDTWithDiscriminatorJsonEncoder(respBody).noSpacesSortKeys
      reqJsonBody shouldEqual """{"d":23.4,"i":123,"s":"a string","type":"SubtypeWithD1"}"""
      respJsonBody shouldEqual """{"d":23.4,"i":123,"s":"a string+SubtypeWithD1","type":"SubA"}"""
      Await.result(
        sttp.client3.basicRequest
          .post(uri"http://test.com/adt/test")
          .body(reqJsonBody)
          .send(stub)
          .map { resp =>
            resp.code.code === 200
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
      reqJsonBody shouldEqual """{"a":["string 1","string 2"],"s":"a string","type":"SubtypeWithD2"}"""
      respJsonBody shouldEqual """{"a":["string 1","string 2"],"s":"a string+SubtypeWithD2","type":"SubB"}"""
      Await.result(
        sttp.client3.basicRequest
          .post(uri"http://test.com/adt/test")
          .body(reqJsonBody)
          .send(stub)
          .map { resp =>
            resp.code.code === 200
            resp.body.map(normalise) shouldEqual Right(respJsonBody)
          },
        1.second
      )
    }

  }
}
