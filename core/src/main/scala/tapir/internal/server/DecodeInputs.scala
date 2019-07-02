package tapir.internal.server

import tapir.model.{Cookie, Method, MultiQueryParams, ServerRequest}
import tapir.{DecodeFailure, DecodeResult, EndpointIO, EndpointInput, MediaType}
import tapir.internal._

import scala.annotation.tailrec

trait DecodeInputsResult
object DecodeInputsResult {

  /**
    * @param basicInputsValues Values of basic inputs, in order as they are defined in the endpoint.
    */
  case class Values(basicInputsValues: Vector[Any], bodyInputWithIndex: Option[(EndpointIO.Body[_, _, _], Int)])
      extends DecodeInputsResult {

    def addBodyInput(input: EndpointIO.Body[_, _ <: MediaType, _], bodyIndex: Int): Values = {
      if (bodyInputWithIndex.isDefined) {
        throw new IllegalStateException(s"Double body definition: $input")
      }

      copy(bodyInputWithIndex = Some((input, bodyIndex)))
    }
    def bodyInput: Option[EndpointIO.Body[_, _, _]] = bodyInputWithIndex.map(_._1)

    /**
      * Sets the value of the body input, once it is known, if a body input is defined.
      */
    def setBodyInputValue(v: Any): Values = bodyInputWithIndex match {
      case Some((_, i)) => copy(basicInputsValues = basicInputsValues.updated(i, v))
      case None         => this
    }

    def setBasicInputValue(v: Any, i: Int): Values = copy(basicInputsValues = basicInputsValues.updated(i, v))
  }
  case class Failure(input: EndpointInput.Basic[_], failure: DecodeFailure) extends DecodeInputsResult
}

trait DecodeInputsContext {
  def method: Method

  def nextPathSegment: (Option[String], DecodeInputsContext)

  def header(name: String): List[String]
  def headers: Seq[(String, String)]

  def queryParameter(name: String): Seq[String]
  def queryParameters: Map[String, Seq[String]]

  def bodyStream: Any

  def serverRequest: ServerRequest
}

object DecodeInputs {

  /**
    * Decodes values of all inputs defined by the given `input`, and returns a map from the input to the input's value.
    *
    * An exception is the body input, which is not decoded. This is because typically bodies can be only read once.
    * That's why, all non-body inputs are used to decide if a request matches the endpoint, or not. If a body input
    * is present, it is also returned as part of the result.
    *
    * In case any of the decoding fails, the failure is returned together with the failing input.
    */
  def apply(input: EndpointInput[_], ctx: DecodeInputsContext): DecodeInputsResult = {
    // The first decoding failure is returned.
    // We decode in the following order: method, path, query, headers (incl. cookies), request, status, body
    // An exact-path check is done after method & path matching

    val basicInputs = assignInputIndexes(input.asVectorOfBasicInputs(), 0, Vector.empty)

    val methodInputs = basicInputs.filter(t => isRequestMethod(t._1))
    val pathInputs = basicInputs.filter(t => isPath(t._1))
    val otherInputs = basicInputs.filterNot(t => isRequestMethod(t._1) || isPath(t._1)).sortBy(t => basicInputSortIndex(t._1))

    // we're using null as a placeholder for the future values. All except the body (which is determined by
    // interpreter-specific code), should be filled by the end of this method.
    compose(
      apply(methodInputs, _, _),
      apply(pathInputs, _, _),
      (values, ctx) =>
        verifyPathExactMatch(pathInputs, ctx) match {
          case None          => (values, ctx)
          case Some(failure) => (failure, ctx)
        },
      apply(otherInputs, _, _)
    )(DecodeInputsResult.Values(Vector.fill(basicInputs.size)(null), None), ctx)._1
  }

  private def apply(
      inputs: Vector[(EndpointInput.Basic[_], Int)],
      values: DecodeInputsResult.Values,
      ctx: DecodeInputsContext
  ): (DecodeInputsResult, DecodeInputsContext) = {
    inputs match {
      case Vector() => (values, ctx)

      case (input @ EndpointInput.FixedMethod(m), _) +: inputsTail =>
        if (m == ctx.method) apply(inputsTail, values, ctx)
        else (DecodeInputsResult.Failure(input, DecodeResult.Mismatch(m.m, ctx.method.m)), ctx)

      case (input @ EndpointInput.FixedPath(ss), _) +: inputsTail =>
        ctx.nextPathSegment match {
          case (Some(`ss`), ctx2)       => apply(inputsTail, values, ctx2)
          case (None, ctx2) if ss == "" => apply(inputsTail, values, ctx2) // root path
          case (Some(s), _)             => (DecodeInputsResult.Failure(input, DecodeResult.Mismatch(ss, s)), ctx)
          case (None, _)                => (DecodeInputsResult.Failure(input, DecodeResult.Missing), ctx)
        }

      case (input @ EndpointInput.PathCapture(codec, _, _), index) +: inputsTail =>
        ctx.nextPathSegment match {
          case (Some(s), ctx2) =>
            codec.safeDecode(s) match {
              case DecodeResult.Value(v)  => apply(inputsTail, values.setBasicInputValue(v, index), ctx2)
              case failure: DecodeFailure => (DecodeInputsResult.Failure(input, failure), ctx)
            }
          case (None, _) => (DecodeInputsResult.Failure(input, DecodeResult.Missing), ctx)
        }

      case (EndpointInput.PathsCapture(_), index) +: inputsTail =>
        @tailrec
        def remainingPath(acc: Vector[String], c: DecodeInputsContext): (Vector[String], DecodeInputsContext) = c.nextPathSegment match {
          case (Some(s), c2) => remainingPath(acc :+ s, c2)
          case (None, c2)    => (acc, c2)
        }

        val (ps, ctx2) = remainingPath(Vector.empty, ctx)

        apply(inputsTail, values.setBasicInputValue(ps, index), ctx2)

      case (input @ EndpointInput.Query(name, codec, _), index) +: inputsTail =>
        codec.safeDecode(ctx.queryParameter(name).toList) match {
          case DecodeResult.Value(v)  => apply(inputsTail, values.setBasicInputValue(v, index), ctx)
          case failure: DecodeFailure => (DecodeInputsResult.Failure(input, failure), ctx)
        }

      case (EndpointInput.QueryParams(_), index) +: inputsTail =>
        apply(inputsTail, values.setBasicInputValue(MultiQueryParams.fromMultiMap(ctx.queryParameters), index), ctx)

      case (input @ EndpointInput.Cookie(name, codec, _), index) +: inputsTail =>
        val allCookies = DecodeResult.sequence(ctx.headers.filter(_._1 == Cookie.HeaderName).map(p => Cookie.parse(p._2)).toList)
        val cookieValue =
          allCookies.map(_.flatten.find(_.name == name)).flatMap(cookie => codec.safeDecode(cookie.map(_.value)))
        cookieValue match {
          case DecodeResult.Value(v)  => apply(inputsTail, values.setBasicInputValue(v, index), ctx)
          case failure: DecodeFailure => (DecodeInputsResult.Failure(input, failure), ctx)
        }

      case (input @ EndpointIO.Header(name, codec, _), index) +: inputsTail =>
        codec.safeDecode(ctx.header(name)) match {
          case DecodeResult.Value(v)  => apply(inputsTail, values.setBasicInputValue(v, index), ctx)
          case failure: DecodeFailure => (DecodeInputsResult.Failure(input, failure), ctx)
        }

      case (EndpointIO.Headers(_), index) +: inputsTail =>
        apply(inputsTail, values.setBasicInputValue(ctx.headers, index), ctx)

      case (EndpointInput.ExtractFromRequest(f), index) +: inputsTail =>
        apply(inputsTail, values.setBasicInputValue(f(ctx.serverRequest), index), ctx)

      case (input @ EndpointIO.Body(_, _), index) +: inputsTail =>
        apply(inputsTail, values.addBodyInput(input, index), ctx)

      case (EndpointIO.StreamBodyWrapper(_), index) +: inputsTail =>
        apply(inputsTail, values.setBasicInputValue(ctx.bodyStream, index), ctx)
    }
  }

  /**
    * If there's any path input, the path must match exactly.
    */
  private def verifyPathExactMatch(
      pathInputs: Vector[(EndpointInput.Basic[_], Int)],
      ctx: DecodeInputsContext
  ): Option[DecodeInputsResult.Failure] = {
    pathInputs.lastOption match {
      case Some((lastPathInput, _)) =>
        ctx.nextPathSegment._1 match {
          case Some(nextPathSegment) =>
            Some(DecodeInputsResult.Failure(lastPathInput, DecodeResult.Mismatch("", nextPathSegment)))
          case None => None
        }

      case None => None
    }
  }

  private val isRequestMethod: EndpointInput.Basic[_] => Boolean = {
    case _: EndpointInput.FixedMethod => true
    case _                            => false
  }

  private val isPath: EndpointInput.Basic[_] => Boolean = {
    case _: EndpointInput.FixedPath      => true
    case _: EndpointInput.PathCapture[_] => true
    case _: EndpointInput.PathsCapture   => true
    case _                               => false
  }

  private type DecodeInputResultTransform = (DecodeInputsResult.Values, DecodeInputsContext) => (DecodeInputsResult, DecodeInputsContext)
  private def compose(fs: DecodeInputResultTransform*): DecodeInputResultTransform = { (values, ctx) =>
    fs match {
      case f +: tail =>
        f(values, ctx) match {
          case (values2: DecodeInputsResult.Values, ctx2) => compose(tail: _*)(values2, ctx2)
          case r                                          => r
        }
      case _ => (values, ctx)
    }
  }

  private val NoIndex = -1

  /**
    * Each basic input produces either 0 or 1 value. Assigning an index (which will be used to fill in a value int
    * the `basicInputsValues` vector later) only to those inputs, which do produce a value.
    */
  @tailrec
  private def assignInputIndexes(
      inputs: Vector[EndpointInput.Basic[_]],
      nextIndex: Int,
      acc: Vector[(EndpointInput.Basic[_], Int)]
  ): Vector[(EndpointInput.Basic[_], Int)] = {
    inputs match {
      case Vector()                                   => acc
      case (input: EndpointInput.FixedMethod) +: tail => assignInputIndexes(tail, nextIndex, acc :+ ((input, NoIndex)))
      case (input: EndpointInput.FixedPath) +: tail   => assignInputIndexes(tail, nextIndex, acc :+ ((input, NoIndex)))
      case input +: tail                              => assignInputIndexes(tail, nextIndex + 1, acc :+ ((input, nextIndex)))
    }
  }

  //

  def rawBodyValueToOption(v: Any, allowsOption: Boolean): Option[Any] = {
    v match {
      case "" if allowsOption => None
      case _                  => Some(v)
    }
  }
}
