package tapir.client

import com.softwaremill.sttp.{Request, Uri}
import tapir.Endpoint
import tapir.typelevel.ParamsAsArgs

package object sttp {
  implicit class RichEndpoint[I, E, O](val e: Endpoint[I, E, O]) extends AnyVal {
    def toSttpRequest(baseUri: Uri)(implicit paramsAsArgs: ParamsAsArgs[I]): paramsAsArgs.FN[Request[Either[E, O], Nothing]] =
      EndpointToSttpClient.toSttpRequest(e, baseUri)
  }
}
