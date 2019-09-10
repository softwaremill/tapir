package tapir
import tapir.model.{Method, StatusCode}

import scala.collection.immutable.ListMap

package object internal {
  implicit class RichEndpointInput[I](input: EndpointInput[I]) {
    def asVectorOfSingleInputs: Vector[EndpointInput.Single[_]] = input match {
      case s: EndpointInput.Single[_]   => Vector(s)
      case m: EndpointInput.Multiple[_] => m.inputs
      case m: EndpointIO.Multiple[_]    => m.ios
    }

    def traverseInputs[T](handle: PartialFunction[EndpointInput[_], Vector[T]]): Vector[T] = input match {
      case i: EndpointInput[_] if handle.isDefinedAt(i) => handle(i)
      case EndpointInput.Multiple(inputs)               => inputs.flatMap(_.traverseInputs(handle))
      case EndpointIO.Multiple(inputs)                  => inputs.flatMap(_.traverseInputs(handle))
      case EndpointInput.Mapped(wrapped, _, _)          => wrapped.traverseInputs(handle)
      case EndpointIO.Mapped(wrapped, _, _)             => wrapped.traverseInputs(handle)
      case a: EndpointInput.Auth[_]                     => a.input.traverseInputs(handle)
      case _                                            => Vector.empty
    }

    def asVectorOfBasicInputs(includeAuth: Boolean = true): Vector[EndpointInput.Basic[_]] = traverseInputs {
      case b: EndpointInput.Basic[_] => Vector(b)
      case a: EndpointInput.Auth[_]  => if (includeAuth) a.input.asVectorOfBasicInputs(includeAuth) else Vector.empty
    }

    def auths: Vector[EndpointInput.Auth[_]] = traverseInputs {
      case a: EndpointInput.Auth[_] => Vector(a)
    }

    def method: Option[Method] =
      traverseInputs {
        case i: EndpointInput.FixedMethod => Vector(i.m)
      }.headOption
  }

  def basicInputSortIndex(i: EndpointInput.Basic[_]): Int = i match {
    case _: EndpointInput.FixedMethod           => 0
    case _: EndpointInput.FixedPath             => 1
    case _: EndpointInput.PathCapture[_]        => 1
    case _: EndpointInput.PathsCapture          => 1
    case _: EndpointInput.Query[_]              => 2
    case _: EndpointInput.QueryParams           => 2
    case _: EndpointInput.Cookie[_]             => 3
    case _: EndpointIO.Header[_]                => 3
    case _: EndpointIO.Headers                  => 3
    case _: EndpointIO.FixedHeader              => 3
    case _: EndpointInput.ExtractFromRequest[_] => 4
    case _: EndpointIO.Body[_, _, _]            => 6
    case _: EndpointIO.StreamBodyWrapper[_, _]  => 6
  }

  implicit class RichEndpointOutput[I](output: EndpointOutput[I]) {
    def asVectorOfSingleOutputs: Vector[EndpointOutput.Single[_]] = output match {
      case s: EndpointOutput.Single[_]   => Vector(s)
      case m: EndpointOutput.Multiple[_] => m.outputs
      case m: EndpointIO.Multiple[_]     => m.ios
    }

    // Outputs may differ basing on status code because of `oneOf`. This method extracts the status code
    // mapping to the top-level. In the map, the `None` key stands for the default status code, and a `Some` value
    // to the status code specified using `statusMapping` or `statusCode(_)`.
    type BasicOutputs = Vector[EndpointOutput.Basic[_]]
    def asBasicOutputsMap: ListMap[Option[StatusCode], BasicOutputs] = asBasicOutputsOrMap match {
      case Left(outputs) => ListMap(None -> outputs)
      case Right(map)    => map
    }

    private[internal] type BasicOutputsOrMap = Either[BasicOutputs, ListMap[Option[StatusCode], BasicOutputs]]
    private[internal] def asBasicOutputsOrMap: BasicOutputsOrMap = {

      def throwMultipleOneOfMappings = throw new IllegalArgumentException(s"Multiple one-of mappings in output $output")

      def mergeMultiple(v: Vector[BasicOutputsOrMap]): BasicOutputsOrMap = v.foldLeft(Left(Vector.empty): BasicOutputsOrMap) {
        case (Left(os1), Left(os2))    => Left(os1 ++ os2)
        case (Left(os1), Right(osMap)) => Right(osMap.map { case (sc, os2) => sc -> (os1 ++ os2) })
        case (Right(osMap), Left(os2)) => Right(osMap.map { case (sc, os1) => sc -> (os1 ++ os2) })
        case (Right(_), Right(_))      => throwMultipleOneOfMappings
      }

      output match {
        case EndpointOutput.Multiple(outputs)     => mergeMultiple(outputs.map(_.asBasicOutputsOrMap))
        case EndpointIO.Multiple(outputs)         => mergeMultiple(outputs.map(_.asBasicOutputsOrMap))
        case EndpointOutput.Mapped(wrapped, _, _) => wrapped.asBasicOutputsOrMap
        case EndpointIO.Mapped(wrapped, _, _)     => wrapped.asBasicOutputsOrMap
        case s: EndpointOutput.OneOf[_] =>
          Right(
            ListMap(
              s.mappings
                .map(c => (c.output.asBasicOutputsOrMap, c.statusCode))
                .map {
                  case (Left(basicOutputs), statusCode) => statusCode -> basicOutputs
                  case (Right(_), _)                    => throwMultipleOneOfMappings
                }: _*
            )
          )
        case f: EndpointOutput.FixedStatusCode => Right(ListMap(Some(f.statusCode) -> Vector(f)))
        case b: EndpointOutput.Basic[_]        => Left(Vector(b))
      }
    }

    private[internal] def traverseOutputs[T](handle: PartialFunction[EndpointOutput[_], Vector[T]]): Vector[T] = output match {
      case o: EndpointOutput[_] if handle.isDefinedAt(o) => handle(o)
      case EndpointOutput.Multiple(outputs)              => outputs.flatMap(_.traverseOutputs(handle))
      case EndpointIO.Multiple(outputs)                  => outputs.flatMap(_.traverseOutputs(handle))
      case EndpointOutput.Mapped(wrapped, _, _)          => wrapped.traverseOutputs(handle)
      case EndpointIO.Mapped(wrapped, _, _)              => wrapped.traverseOutputs(handle)
      case s: EndpointOutput.OneOf[_]                    => s.mappings.toVector.flatMap(_.output.traverseOutputs(handle))
      case _                                             => Vector.empty
    }

    def bodyType: Option[RawValueType[_]] = {
      traverseOutputs[RawValueType[_]] {
        case b: EndpointIO.Body[_, _, _] => Vector(b.codec.meta.rawValueType)
      }.headOption
    }
  }

  implicit class RichBasicEndpointOutputs(outputs: Vector[EndpointOutput.Basic[_]]) {
    def sortByType: Vector[EndpointOutput.Basic[_]] = outputs.sortBy {
      case _: EndpointOutput.StatusCode          => 0
      case _: EndpointOutput.FixedStatusCode     => 0
      case _: EndpointIO.Header[_]               => 1
      case _: EndpointIO.Headers                 => 1
      case _: EndpointIO.FixedHeader             => 1
      case _: EndpointIO.Body[_, _, _]           => 2
      case _: EndpointIO.StreamBodyWrapper[_, _] => 2
    }
  }

  private[tapir] def addValidatorShow(s: String, v: Validator[_]): String = {
    v.show match {
      case None     => s
      case Some(sv) => s"$s($sv)"
    }
  }
}
