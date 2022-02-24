package sttp.tapir.examples.issues

import eu.timepit.refined.collection.NonEmpty
import sttp.client3.{HttpURLConnectionBackend, _}
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.codec.refined._
import sttp.tapir.{Codec, CodecFormat, anyFromUtf8StringBody, endpoint}

//FIXME: move to tests
object Issue1847 extends App {
  val httpStub = HttpURLConnectionBackend.stub.whenAnyRequest.thenRespondOk()

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

  val eitherRefinedRequest = SttpClientInterpreter().toRequest(eitherRefinedEndpoint, Some(uri"localhost")).apply(())
  eitherRefinedRequest.send(httpStub) // Fail
}
