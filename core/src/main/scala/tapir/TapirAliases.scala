package tapir

import tapir.generic.SchemaMagnoliaDerivation

/** Mixin containing aliases for top-level types and modules in the tapir package. */
trait TapirAliases {
  /** Codec.scala */
  type Codec[T, CF <: CodecFormat, R] = tapir.Codec[T, CF, R]
  val Codec = tapir.Codec

  type CodecForOptional[T, CF <: CodecFormat, R] = tapir.CodecForOptional[T, CF, R]
  val CodecForOptional = tapir.CodecForOptional

  type CodecForMany[T, CF <: CodecFormat, R] = tapir.CodecForMany[T, CF, R]
  val CodecForMany = tapir.CodecForMany

  type CodecMeta[T, CF <: CodecFormat, R] = tapir.CodecMeta[T, CF, R]
  val CodecMeta = tapir.CodecMeta

  type RawValueType[R] = tapir.RawValueType[R]
  type StringValueType = tapir.StringValueType

  val ByteArrayValueType = tapir.ByteArrayValueType
  val ByteBufferValueType = tapir.ByteBufferValueType
  val InputStreamValueType = tapir.InputStreamValueType
  val FileValueType = tapir.FileValueType
  type MultipartValueType = tapir.MultipartValueType
  val MultipartValueType = tapir.MultipartValueType

  type Decode[F, T] = tapir.Decode[F, T]

  /** DecodeResult.scala */
  type DecodeResult[+T] = tapir.DecodeResult[T]
  val DecodeResult = tapir.DecodeResult

  type DecodeFailure = tapir.DecodeFailure

  /** Defaults.scala */
  val Defaults = tapir.Defaults

  /** Endpoint.scala */
  type Endpoint[I, E, O, +S] = tapir.Endpoint[I, E, O, S]
  val Endpoint = tapir.Endpoint

  type EndpointInfo = tapir.EndpointInfo
  val EndpointInfo = tapir.EndpointInfo

  /** EndpointIO.scala */
  type EndpointInput[I] = tapir.EndpointInput[I]
  val EndpointInput = tapir.EndpointInput

  type EndpointOutput[O] = tapir.EndpointOutput[O]
  val EndpointOutput = tapir.EndpointOutput

  type EndpointIO[I] = tapir.EndpointIO[I]
  val EndpointIO = tapir.EndpointIO

  type StreamingEndpointIO[I, +S] = tapir.StreamingEndpointIO[I, S]
  val StreamingEndpointIO = tapir.StreamingEndpointIO

  /** CodecFormat.scala */
  type CodecFormat = tapir.CodecFormat
  val CodecFormat = tapir.CodecFormat

  /** package.scala */
  type RawPart = tapir.RawPart
  type AnyPart = tapir.AnyPart
  type AnyCodec = tapir.AnyCodec
  type AnyCodecForMany = tapir.AnyCodecForMany
  type AnyCodecMeta = tapir.AnyCodecMeta

  /** RenderPathTemplate.scala */
  val RenderPathTemplate = tapir.RenderPathTemplate

  /** SchemaType.scala */
  type SchemaType = tapir.SchemaType
  val SchemaType = tapir.SchemaType

  /** Schema.scala */
  type Schema[T] = tapir.Schema[T]
  val Schema: tapir.Schema.type with SchemaMagnoliaDerivation = tapir.Schema

  /** Tapir.scala */
  type Tapir = tapir.Tapir
  type TapirDerivedInputs = tapir.TapirDerivedInputs

  /** TapirAuth.scala */
  val TapirAuth = tapir.TapirAuth
}
