package tapir.client.sttp

import com.softwaremill.sttp.{Request, Uri}
import tapir.Endpoint
import tapir.typelevel.ParamsAsArgs

trait SttpClient {
  implicit class RichEndpoint[I, E, O](e: Endpoint[I, E, O]) {
    def toSttpRequest(baseUri: Uri)(implicit paramsAsArgs: ParamsAsArgs[I],
                                    clientOptions: SttpClientOptions): paramsAsArgs.FN[Request[Either[E, O], Nothing]] =
      new EndpointToSttpClient(clientOptions).toSttpRequest(e, baseUri)
  }
}
