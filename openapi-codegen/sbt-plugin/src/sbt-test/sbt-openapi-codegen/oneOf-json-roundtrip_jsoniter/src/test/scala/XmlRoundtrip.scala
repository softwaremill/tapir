import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import io.circe.parser.parse
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.UriContext
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.generated.TapirGeneratedEndpoints._
import sttp.tapir.generated.TapirGeneratedEndpointsJsonSerdes._
import sttp.tapir.generated.{TapirGeneratedEndpoints, TapirGeneratedEndpointsJsonSerdes, TapirGeneratedEndpointsXmlSerdes}
import sttp.tapir.server.stub.TapirStubInterpreter

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class XmlRoundtrip extends AnyFreeSpec with Matchers {
  "can roundtrip xml" in {
//    val route = TapirGeneratedEndpoints.postXmlEndpoint.serverLogic[Future](Future successful Right(_))
//
//    val stub = TapirStubInterpreter(SttpBackendStub.asynchronousFuture)
//      .whenServerEndpoint(route)
//      .thenRunLogic()
//      .backend()

    locally {
      val reqBody = Pet(
        Some(PetStatus.pending),
        Some(Seq(Tag(Some(1), Some("foo")))),
        Some(2L),
        Seq("uri1", "uri2"),
        "a name",
        Some(Category(Some(3L), Some("a category")))
      )
      val reqXmlBody = TapirGeneratedEndpointsXmlSerdes.PetXmlSerde.encode(reqBody)
      println(s"!!!\n$reqXmlBody\n!!!")
      val decodedXmlBody = TapirGeneratedEndpointsXmlSerdes.PetXmlSerde.decode(reqXmlBody)
      println(s"???\n$decodedXmlBody\n???")
      decodedXmlBody shouldEqual sttp.tapir.DecodeResult.Value(reqBody)
      reqXmlBody shouldEqual
        """<Pet id="2" name="a name">
          | <pending/>
          | <tags>
          |  <Tag id="1" name="foo"/>
          | </tags>
          | <photoUrls>uri1,uri2</photoUrls>
          | <Category id="3" name="a category"/>
          |</Pet>""".stripMargin
//      Await.result(
//        sttp.client3.basicRequest
//          .post(uri"http://test.com/xml/endpoint")
//          .body(reqXmlBody)
//          .header("content-type", "application/xml")
//          .send(stub)
//          .map { resp =>
//            resp.body shouldEqual Right(reqXmlBody)
//            resp.code.code shouldEqual 200
//          },
//        1.second
//      )
    }
  }
}
