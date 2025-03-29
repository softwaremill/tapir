import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.UriContext
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.generated.TapirGeneratedEndpoints
import sttp.tapir.generated.TapirGeneratedEndpoints.OrderStatus.placed
import sttp.tapir.generated.TapirGeneratedEndpoints._
import sttp.tapir.server.stub.TapirStubInterpreter

import java.time.Instant
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
  "can fetch xml and json" in {
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

  "can send xml, json, form" in {
    val route = TapirGeneratedEndpoints.placeOrder
      .serverSecurityLogic[Unit, Future](_ => Future.successful(Right(())))
      .serverLogic { _ =>
        {
          case PlaceOrderBodyOption_Order_In(Some(o)) =>
            Future successful Right(o)
          case PlaceOrderBodyOption_Order_In(None)                 => Future.successful(Right(Order()))
          case PlaceOrderBody2In(bytes) if bytes.isEmpty => Future.successful(Right(Order()))
          case PlaceOrderBody2In(bytes) =>
            val m = new String(bytes, "utf-8").split('&').map(_.split("=", 2)).map { case Array(k, v) => k -> v }.toMap
            Future(
              Order(
                id = m.get("id").map(_.toLong),
                status = m.get("status").map(TapirGeneratedEndpoints.OrderStatus.withName),
                shipDate = m.get("shipDate").map(Instant.parse),
                quantity = m.get("quantity").map(_.toInt),
                complete = m.get("complete").map(_.toBoolean),
                petId = m.get("petId").map(_.toLong)
              )
            ).map(Right(_))
        }
      }

    val stub = TapirStubInterpreter(SttpBackendStub.asynchronousFuture)
      .whenServerEndpoint(route)
      .thenRunLogic()
      .backend()
    val ts = Instant.now

    val order = Order(
      id = Some(123),
      status = Some(placed),
      shipDate = Some(ts),
      quantity = Some(123),
      complete = Some(false),
      petId = Some(98765)
    )
    val formOrder = s"id=123&status=placed&shipDate=${ts}&quantity=123&complete=false&petId=98765"
    val jsonOrder = s"""{"id":123,"status":"placed","shipDate":"$ts","quantity":123,"complete":false,"petId":98765}"""
    val xmlOrder =
      s"""<Order>
         | <id>123</id>
         | <status>placed</status>
         | <shipDate>${ts}</shipDate>
         | <quantity>123</quantity>
         | <complete>false</complete>
         | <petId>98765</petId>
         |</Order>""".stripMargin
    val nullOrder = s"""{"id":null,"status":null,"shipDate":null,"quantity":null,"complete":null,"petId":null}"""
    // ok bodies
    Await.result(
      sttp.client3.basicRequest
        .post(uri"http://test.com/store/order")
        .header("content-type", "application/xml")
        .header("accept", "application/json")
        .header("authorization", "Bearer 1234")
        .body(xmlOrder)
        .send(stub)
        .map { resp =>
          resp.body shouldEqual Right(jsonOrder)
          resp.code.code shouldEqual 200
        },
      1.second
    )
    Await.result(
      sttp.client3.basicRequest
        .post(uri"http://test.com/store/order")
        .header("content-type", "application/x-www-form-urlencoded")
        .header("accept", "application/json")
        .header("authorization", "Bearer 1234")
        .body(formOrder)
        .send(stub)
        .map { resp =>
          resp.body shouldEqual Right(jsonOrder)
          resp.code.code shouldEqual 200
        },
      1.second
    )
    Await.result(
      sttp.client3.basicRequest
        .post(uri"http://test.com/store/order")
        .header("content-type", "application/json")
        .header("accept", "application/json")
        .header("authorization", "Bearer 1234")
        .body(jsonOrder)
        .send(stub)
        .map { resp =>
          resp.body shouldEqual Right(jsonOrder)
          resp.code.code shouldEqual 200
        },
      1.second
    )
    // no body
    Await.result(
      sttp.client3.basicRequest
        .post(uri"http://test.com/store/order")
        .header("content-type", "application/json")
        .header("accept", "application/json")
        .header("authorization", "Bearer 1234")
        .send(stub)
        .map { resp =>
          resp.body shouldEqual Right(nullOrder)
          resp.code.code shouldEqual 200
        },
      1.second
    )
    Await.result(
      sttp.client3.basicRequest
        .post(uri"http://test.com/store/order")
        .header("content-type", "application/x-www-form-urlencoded")
        .header("accept", "application/json")
        .header("authorization", "Bearer 1234")
        .send(stub)
        .map { resp =>
          resp.body shouldEqual Right(nullOrder)
          resp.code.code shouldEqual 200
        },
      1.second
    )
    // Decoding empty xml is failing with 'Invalid value for: body'
//    Await.result(
//      sttp.client3.basicRequest
//        .post(uri"http://test.com/store/order")
//        .header("content-type", "application/xml")
//        .header("accept", "application/json")
//        .header("authorization", "Bearer 1234")
//        .send(stub)
//        .map { resp =>
//          resp.body shouldEqual Right(nullOrder)
//          resp.code.code shouldEqual 200
//        },
//      1.second
//    )
    // invalid bodies
    Await.result(
      sttp.client3.basicRequest
        .post(uri"http://test.com/store/order")
        .header("content-type", "application/xml")
        .header("accept", "application/json")
        .header("authorization", "Bearer 1234")
        .body(jsonOrder)
        .send(stub)
        .map { resp =>
          resp.code.code shouldEqual 400
          resp.body shouldEqual Left("Invalid value for: body")
        },
      1.second
    )
  }
}
