package sttp.tapir

/** Mixin containing aliases for top-level types and modules in the tapir package. */
trait TapirAliases {

  /** attribute.scala */
  type AttributeKey[T] = sttp.tapir.AttributeKey[T]
  // mima val AttributeKey = sttp.tapir.AttributeKey

  /** attribute.scala */
  type AttributeMap = sttp.tapir.AttributeMap
  // mima val AttributeMap = sttp.tapir.AttributeMap

  /** Codec.scala */
  type Codec[L, H, CF <: CodecFormat] = sttp.tapir.Codec[L, H, CF]
  val Codec = sttp.tapir.Codec

  /** Codec.scala */
  type PartCodec[R, T] = sttp.tapir.PartCodec[R, T]
  // mima val PartCodec = sttp.tapir.PartCodec

  /** Codec.scala */
  type MultipartCodec[T] = sttp.tapir.MultipartCodec[T]
  // mima val MultipartCodec = sttp.tapir.MultipartCodec

  /** Codec.scala */
  type RawBodyType[R] = sttp.tapir.RawBodyType[R]
  // mima val RawBodyType = sttp.tapir.RawBodyType

  /** CodecFormat.scala */
  type CodecFormat = sttp.tapir.CodecFormat
  // mima val CodecFormat = sttp.tapir.CodecFormat

  /** DecodeResult.scala */
  type DecodeResult[+T] = sttp.tapir.DecodeResult[T]
  val DecodeResult = sttp.tapir.DecodeResult

  /** Defaults.scala */
  val Defaults = sttp.tapir.Defaults

  /** Endpoint.scala */
  type Endpoint[A, I, E, O, -R] = sttp.tapir.Endpoint[A, I, E, O, R]
  val Endpoint = sttp.tapir.Endpoint

  /** Endpoint.scala */
  type EndpointInfo = sttp.tapir.EndpointInfo
  val EndpointInfo = sttp.tapir.EndpointInfo

  /** EndpointIO.scala */
  type EndpointInput[I] = sttp.tapir.EndpointInput[I]
  val EndpointInput = sttp.tapir.EndpointInput

  /** EndpointIO.scala */
  type EndpointOutput[O] = sttp.tapir.EndpointOutput[O]
  val EndpointOutput = sttp.tapir.EndpointOutput

  /** EndpointIO.scala */
  type EndpointIO[I] = sttp.tapir.EndpointIO[I]
  val EndpointIO = sttp.tapir.EndpointIO

  /** EndpointIO.scala */
  @deprecated("Use StreamBodyIO instead")
  type StreamBody[BS, T, S] = sttp.tapir.StreamBodyIO[BS, T, S]
  @deprecated("Use StreamBodyIO instead")
  val StreamBody = sttp.tapir.StreamBodyIO

  /** EndpointIO.scala */
  type StreamBodyIO[BS, T, S] = sttp.tapir.StreamBodyIO[BS, T, S]
  // mima val StreamBodyIO = sttp.tapir.StreamBodyIO

  /** EndpointIO.scala */
  type WebSocketBodyOutput[PIPE_REQ_RESP, REQ, RESP, T, S] = sttp.tapir.WebSocketBodyOutput[PIPE_REQ_RESP, REQ, RESP, T, S]
  // mima val WebSocketBodyOutput = sttp.tapir.WebSocketBodyOutput

  /** FieldName.scala */
  type FieldName = sttp.tapir.FieldName
  // mima val FieldName = sttp.tapir.FieldName

  /** FileRange.scala */
  type FileRange = sttp.tapir.FileRange
  // mima val FileRange = sttp.tapir.FileRange

  /** FileRange.scala */
  type RangeValue = sttp.tapir.RangeValue
  // mima val RangeValue = sttp.tapir.RangeValue

  /** InputStreamRange.scala */
  type InputStreamRange = sttp.tapir.InputStreamRange
  // mima val InputStreamRange = sttp.tapir.InputStreamRange

  /** package.scala */
  type RawPart = sttp.tapir.RawPart
  type AnyPart = sttp.tapir.AnyPart
  type AnyListCodec = sttp.tapir.AnyListCodec
  type AnyEndpoint = sttp.tapir.AnyEndpoint
  type PublicEndpoint[INPUT, ERROR_OUTPUT, OUTPUT, -R] = sttp.tapir.PublicEndpoint[INPUT, ERROR_OUTPUT, OUTPUT, R]

  /** SchemaType.scala */
  type SchemaType[T] = sttp.tapir.SchemaType[T]
  val SchemaType = sttp.tapir.SchemaType

  /** Schema.scala */
  type Schema[T] = sttp.tapir.Schema[T]
  val Schema: sttp.tapir.Schema.type = sttp.tapir.Schema

  /** SchemaAnnotations.scala */
  type SchemaAnnotations[T] = sttp.tapir.SchemaAnnotations[T]
  // mima val SchemaAnnotations = sttp.tapir.SchemaAnnotations

  /** Tapir.scala */
  type Tapir = sttp.tapir.Tapir

  /** TapirAuth.scala */
  val TapirAuth = sttp.tapir.TapirAuth

  /** Validator.scala */
  // mima val Validator = sttp.tapir.Validator

  /** Validator.scala */
  type ValidationResult = sttp.tapir.ValidationResult
  // mima val ValidationResult = sttp.tapir.ValidationResult

  /** Validator.scala */
  type ValidationError[T] = sttp.tapir.ValidationError[T]
  // mima val ValidationError = sttp.tapir.ValidationError

}
