package sttp.tapir.examples.my

import eu.timepit.refined.collection.NonEmpty
import sttp.client3.{HttpURLConnectionBackend, _}
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.codec.refined._
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.{Codec, CodecFormat, anyFromUtf8StringBody, endpoint}

object Issue1847 extends App {
  val httpStub = HttpURLConnectionBackend.stub.whenAnyRequest.thenRespondOk()

  val eitherEndpoint =
    endpoint
      .out(
        anyFromUtf8StringBody(
          Codec.eitherRight(
            Codec.string,
            Codec.string
          )
        )
      )

  val eitherRefinedEndpoint =
    endpoint
      .out(
        anyFromUtf8StringBody(
          Codec.eitherRight(
            codecForRefined[String, String, NonEmpty, CodecFormat.TextPlain],
            codecForRefined[String, String, NonEmpty, CodecFormat.TextPlain]
          )
        )
      )

//  val eitherRequest = SttpClientInterpreter().toRequest(eitherEndpoint, Some(uri"localhost")).apply(())
//  eitherRequest.send(httpStub) // Works
//
//  OpenAPIDocsInterpreter().toOpenAPI(eitherRefinedEndpoint, "title", "1.0").toYaml // Works

  val eitherRefinedRequest = SttpClientInterpreter().toRequest(eitherRefinedEndpoint, Some(uri"localhost")).apply(())
  eitherRefinedRequest.send(httpStub) // Fail
}
