package sttp.tapir

import java.nio.charset.Charset

import sttp.model.{Method, StatusCode}
import sttp.tapir.typelevel.{BinaryTupleOp, ParamConcat, ParamSubtract}

import scala.collection.immutable.ListMap

package object internal {

  /**
    * A union type: () | value | 2+ tuple. Represents the possible parameters of an endpoint's input/output:
    * no parameters, a single parameter (a "stand-alone" value instead of a 1-tuple), and multiple parameters.
    *
    * There are two views on parameters: [[ParamsAsAny]], where the parameters are represented as instances of
    * the union type, or [[ParamsAsVector]], where the parameters are represented as a vector of size 0/1/2+.
    */
  sealed trait Params {
    def asAny: Any
    def asVector: Vector[Any]
  }
  case class ParamsAsAny(asAny: Any) extends Params {
    override lazy val asVector: Vector[Any] = ParamsToSeq(asAny).toVector
  }
  case class ParamsAsVector(asVector: Vector[Any]) extends Params {
    override lazy val asAny: Any = SeqToParams(asVector)
  }

  type CombineParams = (Params, Params) => Params
  type SplitParams = Params => (Params, Params)

  def mkCombine(op: BinaryTupleOp): CombineParams =
    (op.leftArity, op.rightArity) match {
      case (0, _) => (_, p2) => p2
      case (_, 0) => (p1, _) => p1
      case (1, 1) => (p1, p2) => ParamsAsVector(Vector(p1.asAny, p2.asAny))
      case (1, _) => (p1, p2) => ParamsAsVector(p1.asAny +: p2.asVector)
      case (_, 1) => (p1, p2) => ParamsAsVector(p1.asVector :+ p2.asAny)
      case _      => (p1, p2) => ParamsAsVector(p1.asVector ++ p2.asVector)
    }

  def mkSplit(op: BinaryTupleOp): SplitParams =
    (op.leftArity, op.rightArity) match {
      case (0, _) => p => (ParamsAsAny(()), p)
      case (_, 0) => p => (p, ParamsAsAny(()))
      case (1, 1) => p => (ParamsAsAny(p.asVector.head), ParamsAsAny(p.asVector.last))
      case (1, _) => p => (ParamsAsAny(p.asVector.head), ParamsAsVector(p.asVector.tail))
      case (_, 1) => p => (ParamsAsVector(p.asVector.init), ParamsAsAny(p.asVector.last))
      case (a, b) => p => (ParamsAsVector(p.asVector.take(a)), ParamsAsVector(p.asVector.takeRight(b)))
    }

  def combine[T, U, TU](t: T, u: U)(concat: ParamConcat.Aux[T, U, TU]): TU =
    mkCombine(concat).apply(ParamsAsAny(t), ParamsAsAny(u)).asAny.asInstanceOf[TU]

  def split[T, U, TU](tu: TU)(subtract: ParamSubtract.Aux[TU, T, U]): (T, U) =
    mkSplit(subtract).apply(ParamsAsAny(tu)).asInstanceOf[(T, U)]

  //

  implicit class RichEndpointInput[I](input: EndpointInput[I]) {
    def traverseInputs[T](handle: PartialFunction[EndpointInput[_], Vector[T]]): Vector[T] =
      input match {
        case i: EndpointInput[_] if handle.isDefinedAt(i) => handle(i)
        case EndpointInput.Pair(left, right, _, _)        => left.traverseInputs(handle) ++ right.traverseInputs(handle)
        case EndpointIO.Pair(left, right, _, _)           => left.traverseInputs(handle) ++ right.traverseInputs(handle)
        case EndpointInput.MappedPair(wrapped, _)         => wrapped.traverseInputs(handle)
        case EndpointIO.MappedPair(wrapped, _)            => wrapped.traverseInputs(handle)
        case a: EndpointInput.Auth[_]                     => a.input.traverseInputs(handle)
        case _                                            => Vector.empty
      }

    def asVectorOfBasicInputs(includeAuth: Boolean = true): Vector[EndpointInput.Basic[_]] =
      traverseInputs {
        case b: EndpointInput.Basic[_] => Vector(b)
        case a: EndpointInput.Auth[_]  => if (includeAuth) a.input.asVectorOfBasicInputs(includeAuth) else Vector.empty
      }

    def auths: Vector[EndpointInput.Auth[_]] =
      traverseInputs {
        case a: EndpointInput.Auth[_] => Vector(a)
      }

    def method: Option[Method] =
      traverseInputs {
        case i: EndpointInput.FixedMethod[_] => Vector(i.m)
      }.headOption
  }

  def basicInputSortIndex(i: EndpointInput.Basic[_]): Int =
    i match {
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
      case _: EndpointIO.Empty[_]                 => 7
    }

  implicit class RichEndpointOutput[I](output: EndpointOutput[I]) {
    // Outputs may differ basing on status code because of `oneOf`. This method extracts the status code
    // mapping to the top-level. In the map, the `None` key stands for the default status code, and a `Some` value
    // to the status code specified using `statusMapping` or `statusCode(_)`. Any empty outputs are skipped.
    type BasicOutputs = Vector[EndpointOutput.Basic[_]]
    def asBasicOutputsMap: ListMap[Option[StatusCode], BasicOutputs] =
      asBasicOutputsOrMap match {
        case Left(outputs) => ListMap(None -> outputs)
        case Right(map)    => map
      }

    private[internal] type BasicOutputsOrMap = Either[BasicOutputs, ListMap[Option[StatusCode], BasicOutputs]]
    private[internal] def asBasicOutputsOrMap: BasicOutputsOrMap = {
      def throwMultipleOneOfMappings = throw new IllegalArgumentException(s"Multiple one-of mappings in output $output")

      def mergeMultiple(v: Vector[BasicOutputsOrMap]): BasicOutputsOrMap =
        v.foldLeft(Left(Vector.empty): BasicOutputsOrMap) {
          case (Left(os1), Left(os2))    => Left(os1 ++ os2)
          case (Left(os1), Right(osMap)) => Right(osMap.map { case (sc, os2) => sc -> (os1 ++ os2) })
          case (Right(osMap), Left(os2)) => Right(osMap.map { case (sc, os1) => sc -> (os1 ++ os2) })
          case (Right(_), Right(_))      => throwMultipleOneOfMappings
        }

      output match {
        case EndpointOutput.Pair(left, right, _, _) => mergeMultiple(Vector(left.asBasicOutputsOrMap, right.asBasicOutputsOrMap))
        case EndpointIO.Pair(left, right, _, _)     => mergeMultiple(Vector(left.asBasicOutputsOrMap, right.asBasicOutputsOrMap)) // TODO
        case EndpointOutput.MappedPair(wrapped, _)  => wrapped.asBasicOutputsOrMap
        case EndpointIO.MappedPair(wrapped, _)      => wrapped.asBasicOutputsOrMap
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
        case b: EndpointIO.Empty[_]     => Left(Vector.empty)
        case b: EndpointOutput.Basic[_] => Left(Vector(b))
      }
    }

    private[internal] def traverseOutputs[T](handle: PartialFunction[EndpointOutput[_], Vector[T]]): Vector[T] =
      output match {
        case o: EndpointOutput[_] if handle.isDefinedAt(o) => handle(o)
        case EndpointOutput.Pair(left, right, _, _)        => left.traverseOutputs(handle) ++ right.traverseOutputs(handle)
        case EndpointIO.Pair(left, right, _, _)            => left.traverseOutputs(handle) ++ right.traverseOutputs(handle)
        case EndpointOutput.MappedPair(wrapped, _)         => wrapped.traverseOutputs(handle)
        case EndpointIO.MappedPair(wrapped, _)             => wrapped.traverseOutputs(handle)
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
    def sortByType: Vector[EndpointOutput.Basic[_]] =
      outputs.sortBy {
        case _: EndpointIO.Empty[_]                => 0
        case _: EndpointOutput.StatusCode[_]       => 0
        case _: EndpointOutput.FixedStatusCode[_]  => 0
        case _: EndpointIO.Header[_]               => 1
        case _: EndpointIO.Headers[_]              => 1
        case _: EndpointIO.FixedHeader[_]          => 1
        case _: EndpointIO.Body[_, _]              => 2
        case _: EndpointIO.StreamBodyWrapper[_, _] => 2
      }
  }

  def addValidatorShow(s: String, v: Validator[_]): String = {
    v.show match {
      case None     => s
      case Some(sv) => s"$s($sv)"
    }
  }

  def showMultiple(et: Vector[EndpointTransput[_]]): String = {
    val et2 = et.filter {
      case _: EndpointIO.Empty[_] => false
      case _                      => true
    }
    if (et2.isEmpty) "-" else et2.map(_.show).mkString(" ")
  }

  def showOneOf(mappings: Seq[String]): String = s"status one of(${mappings.mkString("|")})"

  implicit class RichSchema[T](val s: Schema[T]) extends AnyVal {
    def as[U]: Schema[U] = s.asInstanceOf[Schema[U]]
  }

  def charset(bodyType: RawBodyType[_]): Option[Charset] = {
    bodyType match {
      case RawBodyType.StringBody(charset) => Some(charset)
      case _                               => None
    }
  }
}
