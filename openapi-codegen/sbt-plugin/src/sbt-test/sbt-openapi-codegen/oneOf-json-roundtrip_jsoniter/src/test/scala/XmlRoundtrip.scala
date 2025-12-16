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
        Some(2L),
        "a name",
        Some(Category(Some(3L), Some("a category"))),
        Seq("uri1", "uri2"),
        Some(Seq(Tag(Some(1), Some("foo")), Tag(Some(2), None))),
        Some(Seq(Tag2(Some(3), Some("bar")), Tag2(Some(4), None))),
        Some(PetStatus.pending)
      )
      val reqXmlBody = TapirGeneratedEndpointsXmlSerdes.PetXmlEncoder.encode(reqBody)
      val decodedXmlBody = TapirGeneratedEndpointsXmlSerdes.PetXmlDecoder.decode(reqXmlBody)
      decodedXmlBody shouldEqual cats.data.Validated.Valid(reqBody)
      reqXmlBody.toString() shouldEqual
        """<Pet>
          | <id>2</id>
          | <name>a name</name>
          | <category>
          |  <id>3</id>
          |  <name>a category</name>
          | </category>
          | <photoUrls>
          |  <photoUrl>uri1</photoUrl>
          |  <photoUrl>uri2</photoUrl>
          | </photoUrls>
          | <tags>
          |  <tag>
          |   <id>1</id>
          |   <name>foo</name>
          |  </tag>
          |  <tag>
          |   <id>2</id>
          |  </tag>
          | </tags>
          | <extra-tags>
          |  <id>3</id>
          |  <name>bar</name>
          | </extra-tags>
          | <extra-tags>
          |  <id>4</id>
          | </extra-tags>
          | <status>pending</status>
          |</Pet>""".stripMargin
      Await.result(
        sttp.client3.basicRequest
          .post(uri"http://test.com/xml/endpoint")
          .body(reqXmlBody.toString())
          .header("content-type", "application/xml")
          .send(stub)
          .map { resp =>
            resp.body shouldEqual Right(reqXmlBody.toString())
            resp.code.code shouldEqual 200
          },
        1.second
      )
    }
  }
}
