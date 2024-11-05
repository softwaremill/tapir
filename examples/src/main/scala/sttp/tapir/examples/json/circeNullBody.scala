// {cat=JSON; effects=Direct; server=Netty; JSON=circe}: Return a JSON body which optionally serializes as `null`

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.8
//> using dep ch.qos.logback:logback-classic:1.5.8

package sttp.tapir.examples.json

import sttp.tapir.*
import sttp.tapir.server.netty.sync.NettySyncServer
import sttp.tapir.json.circe.*
import sttp.tapir.generic.auto.*
import io.circe.generic.auto.*
import io.circe.{Encoder, Json}

@main def circeNullBody(): Unit =
  // the data class
  case class Data(value: Int)

  // helper class, which will allow us to serialize the response body to `null`
  // this is the same as an `Option`, however a `None` body by default serializes as an empty body (always)
  // integration as below is needed for clients which require the body to be exactly `null`
  enum OrNull[+T]:
    case Value(value: T)
    case Null

  // we need to overwrite the encoder that is derived for `OrNull` by default using auto-derivation
  // if needed, a `Decoder` would have to be defined in a similar way
  given encodeOrNull[A](using Encoder[A]): Encoder[OrNull[A]] = new Encoder[OrNull[A]]:
    def apply(a: OrNull[A]): Json =
      a match
        case OrNull.Value(v) => summon[Encoder[A]](v)
        case OrNull.Null     => Json.Null

  val helloWorld = endpoint.get
    .in("hello")
    .in(query[Int]("value"))
    .out(jsonBody[OrNull[Data]])
    .handleSuccess(v => if v == 0 then OrNull.Null else OrNull.Value(Data(v)))

  NettySyncServer().addEndpoint(helloWorld).startAndWait()
