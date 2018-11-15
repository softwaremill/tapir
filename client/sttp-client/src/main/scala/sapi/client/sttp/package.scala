package sapi.client

import com.softwaremill.sttp.Request
import sapi.Endpoint
import sapi.typelevel.ParamsToFn

package object sttp {
  implicit class RichEndpoint[I, E, O](val e: Endpoint[I, E, O]) extends AnyVal {
    def toSttpRequest[FN[_]](host: String)(implicit paramsToFn: ParamsToFn[I, FN]): FN[Request[Either[E, O], Nothing]] =
      EndpointToSttpClient.toSttpRequest(e, host)
  }
}
