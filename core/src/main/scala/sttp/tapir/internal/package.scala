package sttp.tapir

import sttp.model.{ContentTypeRange, HeaderNames, MediaType, Method}
import sttp.monad.MonadError
import sttp.tapir.EndpointOutput.WebSocketBodyWrapper
import sttp.tapir.typelevel.BinaryTupleOp

import java.nio.charset.{Charset, StandardCharsets}
import scala.collection.immutable
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

package object internal {
  // some definitions are intentionally left public as they are used in server/client interpreters

  /** A union type: () | value | 2+ tuple. Represents the possible parameters of an endpoint's input/output: no parameters, a single
    * parameter (a "stand-alone" value instead of a 1-tuple), and multiple parameters.
    *
    * There are two views on parameters: [[ParamsAsAny]], where the parameters are represented as instances of the union type, or
    * [[ParamsAsVector]], where the parameters are represented as a vector of size 0/1/2+.
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

  private[tapir] def mkCombine(op: BinaryTupleOp): CombineParams =
    (op.leftArity, op.rightArity) match {
      case (0, _) => (_, p2) => p2
      case (_, 0) => (p1, _) => p1
      case (1, 1) => (p1, p2) => ParamsAsVector(Vector(p1.asAny, p2.asAny))
      case (1, _) => (p1, p2) => ParamsAsVector(p1.asAny +: p2.asVector)
      case (_, 1) => (p1, p2) => ParamsAsVector(p1.asVector :+ p2.asAny)
      case _      => (p1, p2) => ParamsAsVector(p1.asVector ++ p2.asVector)
    }

  private[tapir] def mkSplit(op: BinaryTupleOp): SplitParams =
    (op.leftArity, op.rightArity) match {
      case (0, _) => p => (ParamsAsAny(()), p)
      case (_, 0) => p => (p, ParamsAsAny(()))
      case (1, 1) => p => (ParamsAsAny(p.asVector.head), ParamsAsAny(p.asVector.last))
      case (1, _) => p => (ParamsAsAny(p.asVector.head), ParamsAsVector(p.asVector.tail))
      case (_, 1) => p => (ParamsAsVector(p.asVector.init), ParamsAsAny(p.asVector.last))
      case (a, b) => p => (ParamsAsVector(p.asVector.take(a)), ParamsAsVector(p.asVector.takeRight(b)))
    }

  //

  implicit class RichEndpoint[A, I, E, O, R](endpoint: Endpoint[A, I, E, O, R]) {
    private def allInputs = endpoint.securityInput.and(endpoint.input)

    def auths: Vector[EndpointInput.Auth[?, ? <: EndpointInput.AuthType]] =
      allInputs.traverseInputs { case a: EndpointInput.Auth[?, ?] =>
        Vector(a)
      }

    def asVectorOfBasicInputs(includeAuth: Boolean = true): Vector[EndpointInput.Basic[?]] = allInputs.asVectorOfBasicInputs(includeAuth)
  }

  implicit class RichEndpointInput[I](input: EndpointInput[I]) {
    def traverseInputs[T](handle: PartialFunction[EndpointInput[?], Vector[T]]): Vector[T] =
      input match {
        case i: EndpointInput[?] if handle.isDefinedAt(i) => handle(i)
        case EndpointInput.Pair(left, right, _, _)        => left.traverseInputs(handle) ++ right.traverseInputs(handle)
        case EndpointIO.Pair(left, right, _, _)           => left.traverseInputs(handle) ++ right.traverseInputs(handle)
        case EndpointInput.MappedPair(wrapped, _)         => wrapped.traverseInputs(handle)
        case EndpointIO.MappedPair(wrapped, _)            => wrapped.traverseInputs(handle)
        case a: EndpointInput.Auth[?, ?]                  => a.input.traverseInputs(handle)
        case _                                            => Vector.empty
      }

    def asVectorOfBasicInputs(includeAuth: Boolean = true): Vector[EndpointInput.Basic[?]] =
      traverseInputs {
        case b: EndpointInput.Basic[?]   => Vector(b)
        case a: EndpointInput.Auth[?, ?] => if (includeAuth) a.input.asVectorOfBasicInputs(includeAuth) else Vector.empty
      }

    def method: Option[Method] =
      traverseInputs { case i: EndpointInput.FixedMethod[?] =>
        Vector(i.m)
      }.headOption

    def pathTo(targetInput: EndpointInput[?]): Vector[EndpointInput[?]] = {
      def findIn(parent: EndpointInput[?], inputs: EndpointInput[?]*) = inputs.foldLeft(Vector.empty[EndpointInput[?]]) {
        case (v, input) if v.isEmpty =>
          val path = input.pathTo(targetInput)
          if (path.nonEmpty) parent +: path else path
        case (v, _) => v
      }
      if (targetInput == input) Vector(input)
      else
        input match {
          case _: EndpointInput.Basic[?]                 => Vector.empty
          case i @ EndpointInput.Pair(left, right, _, _) => findIn(i, left, right)
          case i @ EndpointIO.Pair(left, right, _, _)    => findIn(i, left, right)
          case a: EndpointInput.Auth[?, ?]               => findIn(a, a.input)
          case i @ EndpointInput.MappedPair(p, _)        => findIn(i, p)
          case i @ EndpointIO.MappedPair(p, _)           => findIn(i, p)
        }
    }
  }

  def basicInputSortIndex(i: EndpointInput.Basic[?]): Int =
    i match {
      case _: EndpointInput.FixedMethod[?]        => 0
      case _: EndpointInput.FixedPath[?]          => 1
      case _: EndpointInput.PathCapture[?]        => 1
      case _: EndpointInput.PathsCapture[?]       => 1
      case _: EndpointInput.Query[?]              => 2
      case _: EndpointInput.QueryParams[?]        => 2
      case _: EndpointInput.Cookie[?]             => 3
      case _: EndpointIO.Header[?]                => 3
      case _: EndpointIO.Headers[?]               => 3
      case _: EndpointIO.FixedHeader[?]           => 3
      case _: EndpointInput.ExtractFromRequest[?] => 4
      case _: EndpointIO.Body[?, ?]               => 6
      case _: EndpointIO.StreamBodyWrapper[?, ?]  => 6
      case _: EndpointIO.OneOfBody[?, ?]          => 6
      case _: EndpointIO.Empty[?]                 => 7
    }

  implicit class RichEndpointOutput[I](output: EndpointOutput[I]) {
    // Outputs may have many variants because of `oneOf`. This method extracts the status code
    // mapping to the top-level. In the map, the `None` key stands for the default status code, and a `Some` value
    // to the status code specified using `statusMapping` or `statusCode(_)`. Any empty outputs without metadata are skipped.
    type BasicOutputs = Vector[EndpointOutput.Basic[?]]
    def asBasicOutputsList: List[BasicOutputs] = {
      def product(l: List[BasicOutputs], r: List[BasicOutputs]): List[BasicOutputs] = l.flatMap(o1 => r.map(o2 => o1 ++ o2))

      output match {
        case EndpointOutput.Pair(left, right, _, _) => product(left.asBasicOutputsList, right.asBasicOutputsList)
        case EndpointIO.Pair(left, right, _, _)     => product(left.asBasicOutputsList, right.asBasicOutputsList)
        case EndpointOutput.MappedPair(wrapped, _)  => wrapped.asBasicOutputsList
        case EndpointIO.MappedPair(wrapped, _)      => wrapped.asBasicOutputsList
        case _: EndpointOutput.Void[?]              => List(Vector.empty)
        case s: EndpointOutput.OneOf[?, ?]          => s.variants.flatMap(_.output.asBasicOutputsList)
        case EndpointIO.OneOfBody(variants, _)      => variants.flatMap(_.body.fold(_.asBasicOutputsList, _.asBasicOutputsList))
        case e: EndpointIO.Empty[?]                 => if (hasMetaData(e)) List(Vector(e)) else List(Vector.empty)
        case b: EndpointOutput.Basic[?]             => List(Vector(b))
      }
    }

    private def hasMetaData(e: EndpointIO.Empty[?]): Boolean = {
      e.info.deprecated || e.info.description.nonEmpty || e.info.attributes.nonEmpty || e.info.examples.nonEmpty
    }

    def traverseOutputs[T](handle: PartialFunction[EndpointOutput[?], Vector[T]]): Vector[T] =
      output match {
        case o: EndpointOutput[?] if handle.isDefinedAt(o) => handle(o)
        case EndpointOutput.Pair(left, right, _, _)        => left.traverseOutputs(handle) ++ right.traverseOutputs(handle)
        case EndpointIO.Pair(left, right, _, _)            => left.traverseOutputs(handle) ++ right.traverseOutputs(handle)
        case EndpointOutput.MappedPair(wrapped, _)         => wrapped.traverseOutputs(handle)
        case EndpointIO.MappedPair(wrapped, _)             => wrapped.traverseOutputs(handle)
        case s: EndpointOutput.OneOf[?, ?]                 => s.variants.toVector.flatMap(_.output.traverseOutputs(handle))
        case _                                             => Vector.empty
      }

    def bodyType: Option[RawBodyType[?]] = {
      traverseOutputs[RawBodyType[?]] {
        case b: EndpointIO.Body[?, ?]          => Vector(b.bodyType)
        case EndpointIO.OneOfBody(variants, _) => variants.flatMap(_.body.fold(body => Some(body.bodyType), _.bodyType)).toVector
      }.headOption
    }

    def supportedMediaTypes: Vector[MediaType] = traverseOutputs {
      case b: EndpointIO.Body[?, ?]              => Vector(b.mediaTypeWithCharset)
      case EndpointIO.OneOfBody(variants, _)     => variants.map(_.mediaTypeWithCharset).toVector
      case b: EndpointIO.StreamBodyWrapper[?, ?] => Vector(b.mediaTypeWithCharset)
      case EndpointIO.FixedHeader(h, _, _) if h.name.equalsIgnoreCase(HeaderNames.ContentType) =>
        MediaType.parse(h.value).toOption.toVector
    }

    def hasOptionalBodyMatchingContent(content: MediaType): Boolean = {
      val contentToMatch = content match {
        // default for text https://tools.ietf.org/html/rfc2616#section-3.7.1, other types has no defaults
        case m @ MediaType(_, _, None, _) if m.isText => m.charset(StandardCharsets.ISO_8859_1.name())
        case m                                        => m
      }

      val contentTypeRange =
        ContentTypeRange(contentToMatch.mainType, contentToMatch.subType, contentToMatch.charset.getOrElse(ContentTypeRange.Wildcard))

      // #2354: if there's no body, treating the output as if it was matching the given content-type (the body might be ignored)
      val supported = supportedMediaTypes
      supported.isEmpty || supported.exists(_.matches(contentTypeRange))
    }
  }

  private[tapir] implicit class RichBasicEndpointOutputs(outputs: Vector[EndpointOutput.Basic[?]]) {
    def sortByType: Vector[EndpointOutput.Basic[?]] =
      outputs.sortBy {
        case _: EndpointIO.Empty[?]                       => 0
        case _: EndpointOutput.StatusCode[?]              => 0
        case _: EndpointOutput.FixedStatusCode[?]         => 0
        case _: EndpointIO.Header[?]                      => 1
        case _: EndpointIO.Headers[?]                     => 1
        case _: EndpointIO.FixedHeader[?]                 => 1
        case _: EndpointIO.Body[?, ?]                     => 2
        case _: EndpointIO.StreamBodyWrapper[?, ?]        => 2
        case _: EndpointIO.OneOfBody[?, ?]                => 2
        case _: EndpointOutput.WebSocketBodyWrapper[?, ?] => 2
      }
  }

  private[tapir] implicit class RichBody[R, T](body: EndpointIO.Body[R, T]) {
    def mediaTypeWithCharset: MediaType = body.codec.format.mediaType.copy(charset = charset(body.bodyType).map(_.name()))
  }

  implicit class RichOneOfBody[O, T](body: EndpointIO.OneOfBody[O, T]) {
    def chooseBodyToDecode(contentType: Option[MediaType]): Option[Either[EndpointIO.Body[?, O], EndpointIO.StreamBodyWrapper[?, O]]] = {
      contentType match {
        case Some(ct) => body.variants.find { case EndpointIO.OneOfBodyVariant(range, _) => ct.matches(range) }
        case None     => Some(body.variants.head)
      }
    }.map(_.body)
  }

  private[tapir] implicit class RichStreamBody[BS, T](body: EndpointIO.StreamBodyWrapper[BS, T]) {
    def mediaTypeWithCharset: MediaType = body.codec.format.mediaType.copy(charset = body.wrapped.charset.map(_.name()))
  }

  private[tapir] def addValidatorShow(s: String, schema: Schema[?]): String = schema.showValidators match {
    case None     => s
    case Some(sv) => s"$s($sv)"
  }

  private[tapir] def showMultiple(et: Vector[EndpointTransput[?]], doShow: EndpointTransput[?] => String): String = {
    val et2 = et.filter {
      case _: EndpointIO.Empty[?] => false
      case _                      => true
    }
    if (et2.isEmpty) "-" else et2.map(doShow).mkString(" ")
  }

  private[tapir] def showOneOf(mappings: List[String]): String = mappings.distinct match {
    case Nil     => ""
    case List(o) => o
    case l       => s"one of(${l.mkString("|")})"
  }

  private[tapir] def charset(bodyType: RawBodyType[?]): Option[Charset] = {
    bodyType match {
      case RawBodyType.StringBody(charset) => Some(charset)
      case _                               => None
    }
  }

  private[tapir] def exactMatch[T: ClassTag](exactValues: Set[T]): PartialFunction[Any, Boolean] = { case v: T =>
    exactValues.contains(v)
  }

  private[tapir] def recoverErrors2[T, U, E, O, F[_]](
      f: T => U => F[O]
  )(implicit eClassTag: ClassTag[E], eIsThrowable: E <:< Throwable): MonadError[F] => T => U => F[Either[E, O]] = {
    implicit monad => t => u =>
      import sttp.monad.syntax._
      Try(f(t)(u).map(Right(_): Either[E, O])) match {
        case Success(value) =>
          monad.handleError(value) {
            case e if eClassTag.runtimeClass.isInstance(e) => wrapException(e)
          }
        case Failure(exception) if eClassTag.runtimeClass.isInstance(exception) => wrapException(exception)
        case Failure(exception)                                                 => monad.error(exception)
      }
  }

  private[tapir] def recoverErrors1[T, E, O, F[_]](
      f: T => F[O]
  )(implicit eClassTag: ClassTag[E], eIsThrowable: E <:< Throwable): MonadError[F] => T => F[Either[E, O]] = { m =>
    val result = recoverErrors2((_: Unit) => f)
    result(m)(())
  }

  private[tapir] def findWebSocket(e: Endpoint[?, ?, ?, ?, ?]): Option[WebSocketBodyWrapper[?, ?]] =
    e.output
      .traverseOutputs[EndpointOutput.WebSocketBodyWrapper[?, ?]] { case ws: EndpointOutput.WebSocketBodyWrapper[?, ?] =>
        Vector(ws)
      }
      .headOption

  private def wrapException[F[_], O, E, I](exception: Throwable)(implicit me: MonadError[F]): F[Either[E, O]] = {
    me.unit(Left(exception.asInstanceOf[E]): Either[E, O])
  }

  // see https://github.com/scala/bug/issues/12186
  private[tapir] implicit class RichVector[T](c: Vector[T]) {
    def headAndTail: Option[(T, Vector[T])] = if (c.isEmpty) None else Some((c.head, c.tail))
    def initAndLast: Option[(Vector[T], T)] = if (c.isEmpty) None else Some((c.init, c.last))
  }

  // TODO: make private[tapir] once Scala3/JS compilation is fixed
  implicit class IterableToListMap[A](xs: Iterable[A]) {
    def toListMap[T, U](implicit ev: A <:< (T, U)): immutable.ListMap[T, U] = {
      val b = immutable.ListMap.newBuilder[T, U]
      for (x <- xs)
        b += x

      b.result()
    }
  }

  // TODO: make private[tapir] once Scala3/JS compilation is fixed
  implicit class SortListMap[K, V](m: immutable.ListMap[K, V]) {
    def sortByKey(implicit ko: Ordering[K]): immutable.ListMap[K, V] = sortBy(_._1)
    def sortBy[B: Ordering](f: ((K, V)) => B): immutable.ListMap[K, V] = {
      m.toList.sortBy(f).toListMap
    }
  }

  private[tapir] implicit class ValidatorSyntax[T](v: Validator[T]) {
    def asPrimitiveValidators: Seq[Validator.Primitive[?]] = {
      def toPrimitives(v: Validator[?]): Seq[Validator.Primitive[?]] = {
        v match {
          case Validator.Mapped(wrapped, _) => toPrimitives(wrapped)
          case Validator.All(validators)    => validators.flatMap(toPrimitives)
          case Validator.Any(validators)    => validators.flatMap(toPrimitives)
          case Validator.Custom(_, _)       => Nil
          case bv: Validator.Primitive[?]   => List(bv)
        }
      }
      toPrimitives(v)
    }

    def inferEnumerationEncode: Validator[T] = {
      v match {
        case Validator.Enumeration(possibleValues, None, name) =>
          if (possibleValues.forall(isBasicValue)) Validator.Enumeration(possibleValues, Some((x: T) => Some(x)), name) else v
        case Validator.Mapped(wrapped, g) => Validator.Mapped(wrapped.inferEnumerationEncode, g)
        case Validator.All(validators)    => Validator.All(validators.map(_.inferEnumerationEncode))
        case Validator.Any(validators)    => Validator.Any(validators.map(_.inferEnumerationEncode))
        case _                            => v
      }
    }
  }

  private[tapir] def isBasicValue(v: Any): Boolean = v match {
    case _: String     => true
    case _: Int        => true
    case _: Long       => true
    case _: Float      => true
    case _: Double     => true
    case _: Boolean    => true
    case _: BigDecimal => true
    case _: BigInt     => true
    case null          => true
    case _             => false
  }
}
