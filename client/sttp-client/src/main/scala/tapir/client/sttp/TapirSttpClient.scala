package tapir.client.sttp

import com.softwaremill.sttp.{Request, Uri}
import tapir.Endpoint
import tapir.typelevel.ParamsAsArgs

trait TapirSttpClient {
  implicit class RichEndpoint[I, E, O, S](e: Endpoint[I, E, O, S]) {
    def toSttpRequest(baseUri: Uri)(implicit paramsAsArgs: ParamsAsArgs[I],
                                    clientOptions: SttpClientOptions): paramsAsArgs.FN[Request[Either[E, O], S]] =
      new EndpointToSttpClient(clientOptions).toSttpRequest(e, baseUri)
  }
}
