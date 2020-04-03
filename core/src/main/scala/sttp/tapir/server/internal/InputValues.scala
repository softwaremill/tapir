package sttp.tapir.server.internal

import sttp.tapir.internal.SeqToParams
import sttp.tapir.{Mapping, DecodeResult, EndpointIO, EndpointInput}
import sttp.tapir.internal._

sealed trait InputValuesResult {
  def flatMap(f: InputValuesResult.Values => InputValuesResult): InputValuesResult
}
object InputValuesResult {
  case class Values(values: List[Any], remainingInputValues: Vector[Any]) extends InputValuesResult {
    override def flatMap(f: Values => InputValuesResult): InputValuesResult = f(this)
  }
  case class Failure(input: EndpointInput[_], failure: DecodeResult.Failure) extends InputValuesResult {
    override def flatMap(f: Values => InputValuesResult): InputValuesResult = this
  }
}

object InputValues {

  /**
    * Returns the values of the inputs in the order specified by `input`, and mapped if necessary using defined mapping
    * functions.
    */
  def apply(input: EndpointInput[_], values: DecodeInputsResult.Values): InputValuesResult =
    apply(input.asVectorOfSingleInputs, values.basicInputsValues)

  /**
    * The given `basicInputValues` should match (in order) values for the basic inputs in `inputs`.
    * @return The list of input values + unused basic input values (to be used for subsequent inputs)
    */
  private def apply(inputs: Vector[EndpointInput.Single[_]], remainingInputValues: Vector[Any]): InputValuesResult = {
    inputs match {
      case Vector() => InputValuesResult.Values(Nil, remainingInputValues)
      case EndpointInput.MappedTuple(wrapped, codec) +: inputsTail =>
        handleMappedTuple(wrapped, codec, inputsTail, remainingInputValues)
      case EndpointIO.MappedTuple(wrapped, codec) +: inputsTail =>
        handleMappedTuple(wrapped, codec, inputsTail, remainingInputValues)
      case (auth: EndpointInput.Auth[_]) +: inputsTail =>
        apply(auth.input +: inputsTail, remainingInputValues)
      case (_: EndpointInput.Basic[_]) +: inputsTail =>
        remainingInputValues match {
          case () +: valuesTail =>
            apply(inputsTail, valuesTail)
          case v +: valuesTail =>
            apply(inputsTail, valuesTail).flatMap {
              case InputValuesResult.Values(vs, basicInputValues2) =>
                InputValuesResult.Values(v :: vs, basicInputValues2)
            }
          case Vector() => throw new IllegalStateException(s"Mismatch between basic input values and basic inputs in $inputs")
        }
    }
  }

  private def handleMappedTuple[II, T](
      wrapped: EndpointInput[II],
      codec: Mapping[II, T],
      inputsTail: Vector[EndpointInput.Single[_]],
      remainingInputValues: Vector[Any]
  ): InputValuesResult = {
    apply(wrapped.asVectorOfSingleInputs, remainingInputValues).flatMap {
      case InputValuesResult.Values(wrappedValue, remainingInputValues2) =>
        apply(inputsTail, remainingInputValues2).flatMap {
          case InputValuesResult.Values(vs, remainingInputValues3) =>
            codec.decode(SeqToParams(wrappedValue).asInstanceOf[II]) match {
              case DecodeResult.Value(v)   => InputValuesResult.Values(v :: vs, remainingInputValues3)
              case f: DecodeResult.Failure => InputValuesResult.Failure(wrapped, f)
            }
        }
    }
  }
}
