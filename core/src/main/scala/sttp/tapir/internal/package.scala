package sttp.tapir

import java.nio.charset.Charset

import sttp.model.{Method, StatusCode}

import scala.collection.immutable.ListMap

package object internal {
  implicit class RichEndpointInput[I](input: EndpointInput[I]) {
    def asVectorOfSingleInputs: Vector[EndpointInput.Single[_]] = input match {
      case s: EndpointInput.Single[_] => Vector(s)
      case m: EndpointInput.Tuple[_]  => m.inputs
      case m: EndpointIO.Tuple[_]     => m.ios
    }

    def traverseInputs[T](handle: PartialFunction[EndpointInput[_], Vector[T]]): Vector[T] = input match {
      case i: EndpointInput[_] if handle.isDefinedAt(i) => handle(i)
      case EndpointInput.Tuple(inputs)                  => inputs.flatMap(_.traverseInputs(handle))
      case EndpointIO.Tuple(inputs)                     => inputs.flatMap(_.traverseInputs(handle))
      case EndpointInput.MappedTuple(wrapped, _)        => wrapped.traverseInputs(handle)
      case EndpointIO.MappedTuple(wrapped, _)           => wrapped.traverseInputs(handle)
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
        case i: EndpointInput.FixedMethod[_] => Vector(i.m)
      }.headOption
  }

  def basicInputSortIndex(i: EndpointInput.Basic[_]): Int = i match {
    case _: EndpointInput.FixedMethod[_]        => 0
    case _: EndpointInput.FixedPath[_]          => 1
    case _: EndpointInput.PathCapture[_]        => 1
    case _: EndpointInput.PathsCapture[_]       => 1
    case _: EndpointInput.Query[_]              => 2
    case _: EndpointInput.QueryParams[_]        => 2
    case _: EndpointInput.Cookie[_]             => 3
    case _: EndpointIO.Header[_]                => 3
    case _: EndpointIO.Headers[_]               => 3
    case _: EndpointIO.FixedHeader[_]           => 3
    case _: EndpointInput.ExtractFromRequest[_] => 4
    case _: EndpointIO.Body[_, _]               => 6
    case _: EndpointIO.StreamBodyWrapper[_, _]  => 6
  }

  implicit class RichEndpointOutput[I](output: EndpointOutput[I]) {
    def asVectorOfSingleOutputs: Vector[EndpointOutput.Single[_]] = output match {
      case s: EndpointOutput.Single[_] => Vector(s)
      case _: EndpointOutput.Void[_]   => Vector()
      case m: EndpointOutput.Tuple[_]  => m.outputs
      case m: EndpointIO.Tuple[_]      => m.ios
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
        case EndpointOutput.Tuple(outputs)          => mergeMultiple(outputs.map(_.asBasicOutputsOrMap))
        case EndpointIO.Tuple(outputs)              => mergeMultiple(outputs.map(_.asBasicOutputsOrMap))
        case EndpointOutput.MappedTuple(wrapped, _) => wrapped.asBasicOutputsOrMap
        case EndpointIO.MappedTuple(wrapped, _)     => wrapped.asBasicOutputsOrMap
        case _: EndpointOutput.Void[_]              => Left(Vector.empty)
        case s: EndpointOutput.OneOf[_, _] =>
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
        case f: EndpointOutput.FixedStatusCode[_] => Right(ListMap(Some(f.statusCode) -> Vector(f)))
        case f: EndpointOutput.StatusCode[_] if f.documentedCodes.nonEmpty =>
          val entries = f.documentedCodes.keys.map(code => Some(code) -> Vector(f)).toSeq
          Right(ListMap(entries: _*))
        case b: EndpointOutput.Basic[_] => Left(Vector(b))
      }
    }

    private[internal] def traverseOutputs[T](handle: PartialFunction[EndpointOutput[_], Vector[T]]): Vector[T] = output match {
      case o: EndpointOutput[_] if handle.isDefinedAt(o) => handle(o)
      case EndpointOutput.Tuple(outputs)                 => outputs.flatMap(_.traverseOutputs(handle))
      case EndpointIO.Tuple(outputs)                     => outputs.flatMap(_.traverseOutputs(handle))
      case EndpointOutput.MappedTuple(wrapped, _)        => wrapped.traverseOutputs(handle)
      case EndpointIO.MappedTuple(wrapped, _)            => wrapped.traverseOutputs(handle)
      case s: EndpointOutput.OneOf[_, _]                 => s.mappings.toVector.flatMap(_.output.traverseOutputs(handle))
      case _                                             => Vector.empty
    }

    def bodyType: Option[RawBodyType[_]] = {
      traverseOutputs[RawBodyType[_]] {
        case b: EndpointIO.Body[_, _] => Vector(b.bodyType)
      }.headOption
    }
  }

  implicit class RichBasicEndpointOutputs(outputs: Vector[EndpointOutput.Basic[_]]) {
    def sortByType: Vector[EndpointOutput.Basic[_]] = outputs.sortBy {
      case _: EndpointOutput.StatusCode[_]       => 0
      case _: EndpointOutput.FixedStatusCode[_]  => 0
      case _: EndpointIO.Header[_]               => 1
      case _: EndpointIO.Headers[_]              => 1
      case _: EndpointIO.FixedHeader[_]          => 1
      case _: EndpointIO.Body[_, _]              => 2
      case _: EndpointIO.StreamBodyWrapper[_, _] => 2
    }
  }

  private[tapir] def addValidatorShow(s: String, v: Validator[_]): String = {
    v.show match {
      case None     => s
      case Some(sv) => s"$s($sv)"
    }
  }

  implicit class RichSchema[T](val s: Schema[T]) extends AnyVal {
    def as[U]: Schema[U] = s.asInstanceOf[Schema[U]]
  }

  private[tapir] def charset(bodyType: RawBodyType[_]): Option[Charset] = {
    bodyType match {
      case RawBodyType.StringBody(charset) => Some(charset)
      case _                               => None
    }
  }
}
