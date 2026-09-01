package sttp.tapir.server

import sttp.model.Method
import sttp.tapir.internal._
import sttp.tapir.{AnyEndpoint, EndpointIO, EndpointInput, RawBodyType}

/** Errors make an endpoint unserveable; warnings describe one that works, but whose published contract probably isn't what was intended. */
private[tapir] case class EndpointBodyProblems(errors: List[String], warnings: List[String]) {
  def ++(other: EndpointBodyProblems): EndpointBodyProblems =
    EndpointBodyProblems(errors ++ other.errors, warnings ++ other.warnings)
}

private[tapir] object EndpointBodyProblems {
  val Empty: EndpointBodyProblems = EndpointBodyProblems(Nil, Nil)
}

/** Verifies that endpoint descriptions are structurally serveable. Run by server interpreters when routes are constructed; warnings are not
  * logged anywhere, so call this directly to assert on them.
  */
private[tapir] object EndpointBodyVerifier {
  def verify(endpoints: List[AnyEndpoint]): EndpointBodyProblems =
    endpoints.map(verifyOne).foldLeft(EndpointBodyProblems.Empty)(_ ++ _)

  private[tapir] def throwOnErrors(problems: EndpointBodyProblems): Unit =
    if (problems.errors.nonEmpty) throw new IllegalArgumentException(problems.errors.mkString("\n"))

  def verifyOne(endpoint: AnyEndpoint): EndpointBodyProblems = {
    val securityInputs = endpoint.securityInput.asVectorOfBasicInputs()
    val ordinaryInputs = endpoint.input.asVectorOfBasicInputs()
    val inputs = securityInputs ++ ordinaryInputs

    val secondary = inputs.collect { case b: EndpointIO.Body[?, ?] if b.isSecondary => b }
    def primaryBodiesOf(basics: Vector[EndpointInput.Basic[?]]): Vector[EndpointInput.Basic[?]] = basics.collect {
      case b: EndpointIO.Body[?, ?] if !b.isSecondary => b
      case b: EndpointIO.OneOfBody[?, ?]              => b
      case b: EndpointIO.StreamBodyWrapper[?, ?]      => b
    }
    val securityPrimaryBodies = primaryBodiesOf(securityInputs)
    val inPrimaryBodies = primaryBodiesOf(ordinaryInputs)
    val primaryBodies = securityPrimaryBodies ++ inPrimaryBodies
    def asAtoms(body: EndpointInput.Basic[?]): Vector[EndpointInput.Basic[?]] = body match {
      case ob: EndpointIO.OneOfBody[?, ?] => ob.variants.map(_.bodyAsAtom).toVector
      case other                          => Vector(other)
    }
    val primaryBodyAtoms: Vector[EndpointInput.Basic[?]] = primaryBodies.flatMap(asAtoms)
    val streamingPrimary = primaryBodyAtoms.exists(_.isInstanceOf[EndpointIO.StreamBodyWrapper[?, ?]])
    val nonReplayablePrimary = primaryBodyAtoms.exists {
      case b: EndpointIO.Body[?, ?] =>
        b.bodyType match {
          case RawBodyType.FileBody         => true
          case _: RawBodyType.MultipartBody => true
          case _                            => false
        }
      case _ => false
    }
    val shown = endpoint.showShort

    // asSecondary can be called on a variant, as oneOfBody takes bodies, but the server interpreters only look for
    // the marker on a top-level body input - so accepting it here would silently fall back to reading the body once
    val secondaryInsideOneOfBody: List[String] =
      inputs
        .collect { case ob: EndpointIO.OneOfBody[?, ?] => ob }
        .collect {
          case ob if ob.variants.map(_.bodyAsAtom).exists { case b: EndpointIO.Body[?, ?] => b.isSecondary; case _ => false } =>
            s"Endpoint $shown marks a oneOfBody variant as secondary. Only a body input used on its own can be " +
              s"secondary; a oneOfBody is always part of the API contract."
        }
        .toList

    val tooManyPrimaries: List[String] =
      if (secondaryInsideOneOfBody.nonEmpty) Nil
      else if (securityPrimaryBodies.nonEmpty && inPrimaryBodies.nonEmpty)
        List(
          s"Endpoint $shown declares a request body in both securityIn and in. Only one may be part of the API " +
            s"contract. If both should decode the same request body, mark the securityIn one: " +
            s"stringBody.asSecondary."
        )
      else if (securityPrimaryBodies.size > 1)
        List(
          s"Endpoint $shown declares more than one request body in securityIn. Only one request body may be part " +
            s"of the API contract."
        )
      else if (inPrimaryBodies.size > 1)
        List(
          s"Endpoint $shown declares more than one request body in in. Only one request body may be part of the " +
            s"API contract."
        )
      else Nil

    val streamWithSecondary =
      if (streamingPrimary && secondary.nonEmpty)
        List(
          s"Endpoint $shown combines a streaming body with a secondary body. The request body can either be " +
            s"streamed lazily or buffered for repeated reads, not both."
        )
      else Nil

    val nonReplayableWithSecondary =
      if (nonReplayablePrimary && secondary.nonEmpty)
        List(
          s"Endpoint $shown combines a file or multipart body with a secondary body. Reading the secondary body " +
            s"consumes the request; the file or multipart body would then be read from an already-drained request."
        )
      else Nil

    val bodyCarryingMethod = endpoint.method.exists(m => m == Method.POST || m == Method.PUT || m == Method.PATCH)
    val secondaryWithoutPrimary =
      if (secondary.nonEmpty && primaryBodies.isEmpty && bodyCarryingMethod)
        List(
          s"Endpoint $shown reads a secondary request body, but no request body is part of the API contract: it " +
            s"will be absent from the documentation and clients will not send it. Either declare the body in `in` " +
            s"as well, or drop asSecondary and use the body input directly."
        )
      else Nil

    val uselessMetadata =
      secondary.filter(b => b.info.description.isDefined || b.info.examples.nonEmpty).map { b =>
        s"Endpoint $shown sets a description or example on the secondary body ${b.show}, which never reaches the " +
          s"documentation, as secondary bodies are excluded from it."
      }

    EndpointBodyProblems(
      errors = secondaryInsideOneOfBody ++ tooManyPrimaries ++ streamWithSecondary ++ nonReplayableWithSecondary,
      warnings = (secondaryWithoutPrimary ++ uselessMetadata).toList
    )
  }
}
