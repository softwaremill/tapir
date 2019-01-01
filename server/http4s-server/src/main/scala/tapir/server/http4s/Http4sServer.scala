package tapir.server.http4s

import cats.Applicative
import cats.data._
import cats.effect.Sync
import cats.implicits._
import org.http4s
import org.http4s.headers.`Content-Type`
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Charset, EntityBody, Header, Headers, HttpRoutes, Request, Response, Status}
import tapir.internal.{ParamsToSeq, SeqToParams}
import tapir.typelevel.ParamsAsArgs
import tapir.{DecodeResult, Endpoint, EndpointIO, EndpointInput, GeneralCodec, MediaType}
import java.nio.charset.{Charset => NioCharset}

trait Http4sServer {
  implicit class RichHttp4sHttpEndpoint[I, E, O](e: Endpoint[I, E, O]) {
    def toHttp4sRoutes[F[_]: Sync, FN[_]](logic: FN[F[Either[E, O]]])(implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): HttpRoutes[F] = {
      EndpointToHttp4sServer.toRoutes(e)(logic)
    }
  }
}
