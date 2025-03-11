import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import io.circe.parser.parse
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.UriContext
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.DecodeResult
import sttp.tapir.generated.TapirGeneratedEndpoints._
import sttp.tapir.generated.TapirGeneratedEndpointsJsonSerdes._
import sttp.tapir.generated.{TapirGeneratedEndpoints, TapirGeneratedEndpointsJsonSerdes, TapirGeneratedEndpointsXmlSerdes}
import sttp.tapir.server.stub.TapirStubInterpreter

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class XmlRoundtrip extends AnyFreeSpec with Matchers {
  "can roundtrip xml" in {
    val route = TapirGeneratedEndpoints.postXmlEndpoint.serverLogic[Future](Future successful Right(_))

    val stub = TapirStubInterpreter(SttpBackendStub.asynchronousFuture)
      .whenServerEndpoint(route)
      .thenRunLogic()
      .backend()

    locally {
      val reqBody = Pet(
        Some(PetStatus.pending),
        Some(Seq(Tag(Some(1), Some("foo")), Tag(Some(2), None))),
        Some(2L),
        Some(Seq(Tag2(Some(3), Some("bar")), Tag2(Some(4), None))),
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
        """<Pet>
          | <status>pending</status>
          | <tags>
          |  <tag>
          |   <id>1</id>
          |   <name>foo</name>
          |  </tag>
          |  <tag>
          |   <id>2</id>
          |  </tag>
          | </tags>
          | <id>2</id>
          | <extra-tags>
          |  <id>3</id>
          |  <name>bar</name>
          | </extra-tags>
          | <extra-tags>
          |  <id>4</id>
          | </extra-tags>
          | <photoUrls>
          |  <photoUrl>uri1</photoUrl>
          |  <photoUrl>uri2</photoUrl>
          | </photoUrls>
          | <name>a name</name>
          | <category>
          |  <id>3</id>
          |  <name>a category</name>
          | </category>
          |</Pet>""".stripMargin
      Await.result(
        sttp.client3.basicRequest
          .post(uri"http://test.com/xml/endpoint")
          .body(reqXmlBody)
          .header("content-type", "application/xml")
          .send(stub)
          .map { resp =>
            resp.body shouldEqual Right(reqXmlBody)
            resp.code.code shouldEqual 200
          },
        1.second
      )
    }
  }
}
