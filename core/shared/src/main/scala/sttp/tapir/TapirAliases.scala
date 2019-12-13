package sttp.tapir

import sttp.tapir.generic.internal.SchemaMagnoliaDerivation

/** Mixin containing aliases for top-level types and modules in the tapir package. */
trait TapirAliases {

  /** Codec.scala */
  type Codec[T, CF <: CodecFormat, R] = sttp.tapir.Codec[T, CF, R]
  val Codec = sttp.tapir.Codec

  type CodecForOptional[T, CF <: CodecFormat, R] = sttp.tapir.CodecForOptional[T, CF, R]
  val CodecForOptional = sttp.tapir.CodecForOptional

  type CodecForMany[T, CF <: CodecFormat, R] = sttp.tapir.CodecForMany[T, CF, R]
  val CodecForMany = sttp.tapir.CodecForMany

  type CodecMeta[T, CF <: CodecFormat, R] = sttp.tapir.CodecMeta[T, CF, R]
  val CodecMeta = sttp.tapir.CodecMeta

  type RawValueType[R] = sttp.tapir.RawValueType[R]
  type StringValueType = sttp.tapir.StringValueType

  val ByteArrayValueType = sttp.tapir.ByteArrayValueType
  val ByteBufferValueType = sttp.tapir.ByteBufferValueType
  val InputStreamValueType = sttp.tapir.InputStreamValueType
  val FileValueType = sttp.tapir.FileValueType
  type MultipartValueType = sttp.tapir.MultipartValueType
  val MultipartValueType = sttp.tapir.MultipartValueType

  type Decode[F, T] = sttp.tapir.Decode[F, T]

  /** DecodeResult.scala */
  type DecodeResult[+T] = sttp.tapir.DecodeResult[T]
  val DecodeResult = sttp.tapir.DecodeResult

  type DecodeFailure = sttp.tapir.DecodeFailure

  /** Defaults.scala */
  val Defaults = sttp.tapir.Defaults

  /** Endpoint.scala */
  type Endpoint[I, E, O, +S] = sttp.tapir.Endpoint[I, E, O, S]
  val Endpoint = sttp.tapir.Endpoint

  type EndpointInfo = sttp.tapir.EndpointInfo
  val EndpointInfo = sttp.tapir.EndpointInfo

  /** EndpointIO.scala */
  type EndpointInput[I] = sttp.tapir.EndpointInput[I]
  val EndpointInput = sttp.tapir.EndpointInput

  type EndpointOutput[O] = sttp.tapir.EndpointOutput[O]
  val EndpointOutput = sttp.tapir.EndpointOutput

  type EndpointIO[I] = sttp.tapir.EndpointIO[I]
  val EndpointIO = sttp.tapir.EndpointIO

  type StreamingEndpointIO[I, +S] = sttp.tapir.StreamingEndpointIO[I, S]
  val StreamingEndpointIO = sttp.tapir.StreamingEndpointIO

  /** CodecFormat.scala */
  type CodecFormat = sttp.tapir.CodecFormat
  val CodecFormat = sttp.tapir.CodecFormat

  /** package.scala */
  type RawPart = sttp.tapir.RawPart
  type AnyPart = sttp.tapir.AnyPart
  type AnyCodec = sttp.tapir.AnyCodec
  type AnyCodecForMany = sttp.tapir.AnyCodecForMany
  type AnyCodecMeta = sttp.tapir.AnyCodecMeta

  /** RenderPathTemplate.scala */
  val RenderPathTemplate = sttp.tapir.RenderPathTemplate

  /** SchemaType.scala */
  type SchemaType = sttp.tapir.SchemaType
  val SchemaType = sttp.tapir.SchemaType

  /** Schema.scala */
  type Schema[T] = sttp.tapir.Schema[T]
  val Schema: sttp.tapir.Schema.type with SchemaMagnoliaDerivation = sttp.tapir.Schema

  /** Tapir.scala */
  type Tapir = sttp.tapir.Tapir
  type TapirDerivedInputs = sttp.tapir.TapirDerivedInputs

  /** TapirAuth.scala */
  val TapirAuth = sttp.tapir.TapirAuth
}
