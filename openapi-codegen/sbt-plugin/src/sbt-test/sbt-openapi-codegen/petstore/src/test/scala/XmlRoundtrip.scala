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
  val pet: Pet = Pet(
    Some(PetStatus.available),
    Some(Seq(Tag(Some(1), Some("tag 1")), Tag(Some(2), Some("tag 2")))),
    Some(123L),
    Seq("http://example.com/image.jpg"),
    "bobby",
    Some(Category(Some(4), Some("cat")))
  )
  val petWithTag = pet.copy(tags = pet.tags.map(ts => ts :+ Tag(name = Some("taggle"))))
  "can roundtrip xml" in {
    val route = TapirGeneratedEndpoints.findPetsByTags
      .serverSecurityLogic[Unit, Future](_ => Future.successful(Right(())))
      .serverLogic { _ => p =>
        val petWithTag = pet.copy(tags = pet.tags.map(ts => ts ++ p.headOption.map(t => Tag(name = Some(t)))))
        Future successful Right(List(petWithTag, pet))
      }

    val stub = TapirStubInterpreter(SttpBackendStub.asynchronousFuture)
      .whenServerEndpoint(route)
      .thenRunLogic()
      .backend()

    locally {
      val respXml = TapirGeneratedEndpoints.FindPetsByTagsResponseSeqEncoder.encode(
        List(petWithTag, pet).asInstanceOf[TapirGeneratedEndpoints.FindPetsByTagsResponse]
      )
      val decodedXmlBody = TapirGeneratedEndpoints.FindPetsByTagsResponseSeqDecoder.decode(respXml)
      decodedXmlBody shouldEqual cats.data.Validated.Valid(List(petWithTag, pet))
      val expectedXml = {
        s"""<Pet>
           | <status>available</status>
           | <tags>
           |  <tags>
           |   <id>1</id>
           |   <name>tag 1</name>
           |  </tags>
           |  <tags>
           |   <id>2</id>
           |   <name>tag 2</name>
           |  </tags>
           |  <tags>
           |   <name>taggle</name>
           |  </tags>
           | </tags>
           | <id>123</id>
           | <photoUrls>
           |  <photoUrl>http://example.com/image.jpg</photoUrl>
           | </photoUrls>
           | <name>bobby</name>
           | <category>
           |  <id>4</id>
           |  <name>cat</name>
           | </category>
           |</Pet>
           |<Pet>
           | <status>available</status>
           | <tags>
           |  <tags>
           |   <id>1</id>
           |   <name>tag 1</name>
           |  </tags>
           |  <tags>
           |   <id>2</id>
           |   <name>tag 2</name>
           |  </tags>
           | </tags>
           | <id>123</id>
           | <photoUrls>
           |  <photoUrl>http://example.com/image.jpg</photoUrl>
           | </photoUrls>
           | <name>bobby</name>
           | <category>
           |  <id>4</id>
           |  <name>cat</name>
           | </category>
           |</Pet>""".stripMargin
      }
      respXml.toString() shouldEqual expectedXml
      Await.result(
        sttp.client3.basicRequest
          .get(uri"http://test.com/pet/findByTags?tags=taggle")
          .header("accept", "application/xml")
          .header("authorization", "Bearer 1234")
          .send(stub)
          .map { resp =>
            resp.body shouldEqual Right(expectedXml)
            resp.code.code shouldEqual 200
          },
        1.second
      )

      val expectedJson =
        """[{"status":"available","tags":[{"id":1,"name":"tag 1"},{"id":2,"name":"tag 2"},{"id":null,"name":"taggle"}],"id":123,"photoUrls":["http://example.com/image.jpg"],"name":"bobby","category":{"id":4,"name":"cat"}},{"status":"available","tags":[{"id":1,"name":"tag 1"},{"id":2,"name":"tag 2"}],"id":123,"photoUrls":["http://example.com/image.jpg"],"name":"bobby","category":{"id":4,"name":"cat"}}]"""
      Await.result(
        sttp.client3.basicRequest
          .get(uri"http://test.com/pet/findByTags?tags=taggle")
          .header("accept", "application/json")
          .header("authorization", "Bearer 1234")
          .send(stub)
          .map { resp =>
            resp.body shouldEqual Right(expectedJson)
            resp.code.code shouldEqual 200
          },
        1.second
      )
    }
  }
}
