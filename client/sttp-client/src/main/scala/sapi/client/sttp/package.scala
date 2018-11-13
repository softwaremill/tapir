package sapi.client

import com.softwaremill.sttp.Request
import sapi.{Endpoint, EndpointLogicFn}
import shapeless.HList

package object sttp {
  type RR[X] = Request[X, Nothing]
  implicit class RichEndpoint[I <: HList, E <: HList, O <: HList](val e: Endpoint[I, O, E]) extends AnyVal {
    def toSttpRequest[FN](host: String)(implicit endpointLogicFn: EndpointLogicFn[I, E, O, RR, FN]): FN =
      EndpointToSttpClient.toSttpRequest(e, host)
  }
}
