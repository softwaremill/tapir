package sttp.tapir.server.internal

import sttp.tapir.internal.SeqToParams
import sttp.tapir.{EndpointIO, EndpointInput}
import sttp.tapir.internal._

object InputValues {

  /**
    * Returns the values of the inputs in the order specified by `input`, and mapped if necessary using defined mapping
    * functions.
    */
  def apply(input: EndpointInput[_], values: DecodeInputsResult.Values): List[Any] =
    apply(input.asVectorOfSingleInputs, values.basicInputsValues)._1

  private type BasicInputValues = Vector[Any]

  /**
    * The given `basicInputValues` should match (in order) values for the basic inputs in `inputs`.
    * @return The list of input values + unused basic input values (to be used for subsequent inputs)
    */
  private def apply(inputs: Vector[EndpointInput.Single[_]], basicInputValues: BasicInputValues): (List[Any], BasicInputValues) = {
    inputs match {
      case Vector() => (Nil, basicInputValues)
      case (_: EndpointInput.FixedMethod) +: inputsTail =>
        apply(inputsTail, basicInputValues)
      case (_: EndpointInput.FixedPath) +: inputsTail =>
        apply(inputsTail, basicInputValues)
      case (_: EndpointIO.FixedHeader) +: inputsTail =>
        apply(inputsTail, basicInputValues)
      case EndpointInput.Mapped(wrapped, f, _) +: inputsTail =>
        handleMapped(wrapped, f, inputsTail, basicInputValues)
      case EndpointIO.Mapped(wrapped, f, _) +: inputsTail =>
        handleMapped(wrapped, f, inputsTail, basicInputValues)
      case (auth: EndpointInput.Auth[_]) +: inputsTail =>
        apply(auth.input +: inputsTail, basicInputValues)
      case (_: EndpointInput.Basic[_]) +: inputsTail =>
        basicInputValues match {
          case v +: valuesTail =>
            val (vs, basicInputValues2) = apply(inputsTail, valuesTail)
            (v :: vs, basicInputValues2)
          case Vector() => throw new IllegalStateException(s"Mismatch between basic input values and basic inputs in $inputs")
        }
    }
  }

  private def handleMapped[II, T](
      wrapped: EndpointInput[II],
      f: II => T,
      inputsTail: Vector[EndpointInput.Single[_]],
      basicInputValues: BasicInputValues
  ): (List[Any], BasicInputValues) = {
    val (wrappedValue, basicInputValues2) = apply(wrapped.asVectorOfSingleInputs, basicInputValues)
    val (vs, basicInputValues3) = apply(inputsTail, basicInputValues2)
    (f.asInstanceOf[Any => Any].apply(SeqToParams(wrappedValue)) :: vs, basicInputValues3)
  }
}
