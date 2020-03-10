package sttp.tapir.server

import sttp.client.monad.MonadError
import sttp.client.monad.syntax._
import sttp.client.{NothingT, SttpBackend, _}
import sttp.tapir.client.sttp._
import sttp.tapir.server.internal.{DecodeInputs, DecodeInputsResult}

package object stub extends SttpStubServer {

  implicit class StubbedEndpoint[I, E, O, F[_]: MonadError](
      serverEndpoint: ServerEndpoint[I, E, O, Nothing, F]
  ) {
    def testUsing(input: I): Option[F[Either[E, O]]] = {
      implicit val stubBackend: SttpBackend[F, Nothing, NothingT] = List(serverEndpoint).toBackendStub
      val req = serverEndpoint.endpoint.toSttpRequestUnsafe(uri"http://test.com").apply(input)

      val decodeInputResult = DecodeInputs(serverEndpoint.endpoint.input, new SttpDecodeInput(req))
      decodeInputResult match {
        case DecodeInputsResult.Failure(_, _) =>
          None
        case DecodeInputsResult.Values(_, _) =>
          Some(req.send().map(_.body))
      }
    }
  }

}
