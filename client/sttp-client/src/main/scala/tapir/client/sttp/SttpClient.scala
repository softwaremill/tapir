package tapir.client.sttp

import com.softwaremill.sttp.{Request, Uri}
import tapir.Endpoint
import tapir.typelevel.ParamsAsArgs

trait SttpClient {
  implicit class RichEndpoint[I, E, O](e: Endpoint[I, E, O]) {
    def toSttpRequest(baseUri: Uri)(implicit paramsAsArgs: ParamsAsArgs[I]): paramsAsArgs.FN[Request[Either[E, O], Nothing]] =
      EndpointToSttpClient.toSttpRequest(e, baseUri)
  }
}
