package sapi.client

import com.softwaremill.sttp.Request
import sapi.EndpointLogicFn.FnFromHList
import sapi.{Endpoint, EndpointLogicFn}
import shapeless.HList

package object sttp {
  type RR[X] = Request[X, Nothing]
  implicit class RichEndpoint[I <: HList, E <: HList, O <: HList](val e: Endpoint[I, O, E]) extends AnyVal {
    def toSttpRequest[FN[_]](host: String)(
        implicit endpointLogicFn: EndpointLogicFn[I, E, O],
        fnFromHList: FnFromHList[I, FN]): FN[Request[Either[endpointLogicFn.TE, endpointLogicFn.TO], Nothing]] =
      EndpointToSttpClient.toSttpRequest(e, host)
  }
}
