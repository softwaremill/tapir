package tapir.internal.server
import tapir.internal.SeqToParams
import tapir.{EndpointIO, EndpointInput}

object InputValues {

  /**
    * Returns the values of the inputs in the order specified by `input`, and mapped if necessary using defined mapping
    * functions.
    */
  def apply(input: EndpointInput[_], values: Map[EndpointInput.Single[_], Any]): List[Any] = apply(input.asVectorOfSingle, values)

  private def apply(inputs: Vector[EndpointInput.Single[_]], values: Map[EndpointInput.Single[_], Any]): List[Any] = {
    inputs match {
      case Vector() => Nil
      case (_: EndpointInput.RequestMethod) +: inputsTail =>
        apply(inputsTail, values)
      case (_: EndpointInput.PathSegment) +: inputsTail =>
        apply(inputsTail, values)
      case EndpointInput.Mapped(wrapped, f, _, _) +: inputsTail =>
        handleMapped(wrapped, f, inputsTail, values)
      case EndpointIO.Mapped(wrapped, f, _, _) +: inputsTail =>
        handleMapped(wrapped, f, inputsTail, values)
      case (input: EndpointInput.Single[_]) +: inputsTail =>
        values(input) :: apply(inputsTail, values)
    }
  }

  private def handleMapped[II, T](wrapped: EndpointInput[II],
                                  f: II => T,
                                  inputsTail: Vector[EndpointInput.Single[_]],
                                  values: Map[EndpointInput.Single[_], Any]): List[Any] = {
    val wrappedValue = apply(wrapped.asVectorOfSingle, values)
    f.asInstanceOf[Any => Any].apply(SeqToParams(wrappedValue)) :: apply(inputsTail, values)
  }
}
