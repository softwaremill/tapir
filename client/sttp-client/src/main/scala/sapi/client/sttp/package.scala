package sapi.client

import com.softwaremill.sttp.Request
import sapi.{Endpoint, HListToResult}
import shapeless.HList
import shapeless.ops.function

package object sttp {
  implicit class RichEndpoint[I <: HList, O <: HList, OE <: HList](val e: Endpoint[I, O, OE]) extends AnyVal {
    def toSttpRequest[F, TO, TOE](host: String)(implicit oToTuple: HListToResult.Aux[O, TO],
                                                oeToTuple: HListToResult.Aux[OE, TOE],
                                                tt: function.FnFromProduct.Aux[I => Request[Either[TOE, TO], Nothing], F]): F =
      EndpointToSttpClient.toSttpRequest(e, host)
  }
}
