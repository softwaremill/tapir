package sapi.client

import com.softwaremill.sttp.Request
import sapi.Endpoint
import sapi.typelevel.ParamsAsFnArgs

package object sttp {
  implicit class RichEndpoint[I, E, O](val e: Endpoint[I, E, O]) extends AnyVal {
    def toSttpRequest[FN[_]](host: String)(implicit paramsToFn: ParamsAsFnArgs[I, FN]): FN[Request[Either[E, O], Nothing]] =
      EndpointToSttpClient.toSttpRequest(e, host)
  }
}
