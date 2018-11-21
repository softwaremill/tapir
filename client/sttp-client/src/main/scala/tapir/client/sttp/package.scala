package tapir.client

import com.softwaremill.sttp.Request
import tapir.Endpoint
import tapir.typelevel.ParamsAsArgs

package object sttp {
  implicit class RichEndpoint[I, E, O](val e: Endpoint[I, E, O]) extends AnyVal {
    def toSttpRequest(host: String)(implicit paramsAsArgs: ParamsAsArgs[I]): paramsAsArgs.FN[Request[Either[E, O], Nothing]] =
      EndpointToSttpClient.toSttpRequest(e, host)
  }
}
