// {cat=Custom types; json=circe}: Supporting custom types, when used in query or path parameters, as well as part of JSON bodies

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.8

package sttp.tapir.examples.custom_types

import io.circe.generic.auto.*
import io.circe.{Decoder, Encoder}
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

object EndpointWithCustomTypes:
  // An over-complicated, example custom type
  trait MyId {
    def id: String
  }
  class MyIdImpl(val id: String) extends MyId

  // Custom type as a path or query parameter: encoding and decoding is fully handled by tapir. We need to provide
  // a custom implicit Codec
  implicit val myIdCodec: Codec[String, MyId, CodecFormat.TextPlain] =
    Codec.string.map[MyId](s => new MyIdImpl(s))(myId => myId.id)
  val endpointWithMyId: PublicEndpoint[MyId, Unit, Unit, Nothing] = endpoint.in("find" / path[MyId])

  // Custom type mapped to json: encoding and decoding is handled by circe. The Codec is automatically derived from a
  // circe Encoder and Decoder. We also need the schema (through the SchemaFor implicit) for documentation.
  case class Person(id: MyId, name: String)

  implicit val myIdSchema: Schema[MyId] = Schema.string
  // custom circe encoders and decoders need to be in-scope as well
  implicit val myIdEncoder: Encoder[MyId] = Encoder.encodeString.contramap(_.id)
  implicit val myIdDecoder: Decoder[MyId] = Decoder.decodeString.map(s => new MyIdImpl(s))
  val endpointWithPerson: PublicEndpoint[Unit, Unit, Person, Nothing] = endpoint.out(jsonBody[Person])
