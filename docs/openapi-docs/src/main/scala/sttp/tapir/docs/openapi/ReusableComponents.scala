package sttp.tapir.docs.openapi

import sttp.apispec.openapi.{Header, Parameter}
import sttp.tapir.docs.apispec.schema.{TSchemaToASchema, calculateUniqueIds}
import sttp.tapir.{AnyEndpoint, EndpointInput, EndpointOutput, EndpointTransput}
import sttp.tapir.internal._

private[openapi] case class ReusableComponents(
    parameterToName: Map[Parameter, String],
    headerToName: Map[(String, Header), String]
) {
  def isEmpty: Boolean = parameterToName.isEmpty && headerToName.isEmpty
  def nonEmpty: Boolean = !isEmpty
}

private[openapi] object ReusableComponents {
  val empty: ReusableComponents = ReusableComponents(Map.empty, Map.empty)

  def markerOf(atom: EndpointTransput.Atom[_]): Option[ReusableComponent] =
    atom.attribute(ReusableComponentAttribute.reusableComponentAttributeKey)

  /** Must run before `nameAllPathCapturesInEndpoint`, which names unnamed captures `p1`, `p2`, ... restarting at `p1` for each endpoint:
    * afterwards a generated name is indistinguishable from one the user wrote, and would be used as a component key.
    */
  def verifyMarkedPathCapturesAreNamed(e: AnyEndpoint): Unit =
    e.asVectorOfBasicInputs(includeAuth = false).foreach {
      case p: EndpointInput.PathCapture[_] if p.name.isEmpty && markerOf(p).exists(_.name.isEmpty) =>
        throw new IllegalStateException(
          "An unnamed path capture is marked as a reusable component, but has no name to use as the component key. " +
            "Name the capture, e.g. path[String](\"id\"), or give the component an explicit key, e.g. .reusableComponent(\"Id\")."
        )
      case _ => ()
    }
}

private[openapi] class ReusableComponentsForEndpoints(
    es: Iterable[AnyEndpoint],
    tschemaToASchema: TSchemaToASchema,
    failOnDuplicateComponentName: Boolean,
    defaultDecodeFailureOutput: EndpointInput[_] => Option[EndpointOutput[_]]
) {
  private val endpointToParameters = new EndpointToParameters(tschemaToASchema)
  private val endpointToHeaders = new EndpointToHeaders(tschemaToASchema)

  def apply(): ReusableComponents =
    ReusableComponents(
      parameterToName = assignNames(collectMarkedParameters(), (p: Parameter) => p.name, "parameters"),
      headerToName = assignNames(collectMarkedHeaders(), (nh: (String, Header)) => nh._1, "headers")
    )

  private def collectMarkedParameters(): Vector[(Parameter, Option[String])] =
    es.toVector.flatMap { e =>
      endpointToParameters
        .withSourceAtoms(endpointToParameters.filterOutHiddenInputs(e.asVectorOfBasicInputs(includeAuth = false)))
        .flatMap { case (atom, parameter) => ReusableComponents.markerOf(atom).map(m => parameter -> m.name) }
    }

  private def collectMarkedHeaders(): Vector[((String, Header), Option[String])] =
    es.toVector.flatMap { e =>
      // the same argument EndpointToOperationResponse passes, so that the generated headers - which the lookup is keyed by - match
      val decodeFailureOutputs = defaultDecodeFailureOutput(e.securityInput.and(e.input)).toList
      endpointToHeaders
        .withSourceAtoms(List(e.output, e.errorOutput) ++ decodeFailureOutputs)
        .toVector
        .flatMap { case (atom, nameAndHeader) => ReusableComponents.markerOf(atom).map(m => nameAndHeader -> m.name) }
    }

  private def assignNames[T](marked: Vector[(T, Option[String])], defaultName: T => String, section: String): Map[T, String] = {
    val distinctMarked: Vector[(T, Option[String])] = marked
      .groupBy(_._1)
      .toVector
      .map { case (t, markers) =>
        val explicitNames = markers.flatMap(_._2).distinct.sorted
        if (explicitNames.size > 1 && failOnDuplicateComponentName)
          throw new IllegalStateException(
            s"Conflicting OpenAPI component names in components/$section: ${explicitNames.mkString(", ")}. " +
              "The same parameter or header cannot be emitted under more than one key - mark it once, " +
              "or set OpenAPIDocsOptions.failOnDuplicateComponentName to false to use the first name alphabetically."
          )
        t -> explicitNames.headOption
      }
      .sortBy { case (t, explicitName) => (explicitName.getOrElse(defaultName(t)), t.toString) }

    val assigned = calculateUniqueIds[(T, Option[String])](
      distinctMarked,
      { case (t, explicitName) => explicitName.getOrElse(defaultName(t)) },
      failOnDuplicateComponentName,
      baseNames =>
        s"Duplicate OpenAPI component names found in components/$section: ${baseNames.mkString(", ")}. " +
          "Components marked as reusable share a name, but have different definitions - this happens when a marked value is modified " +
          "at a use site, e.g. adding an example, as the marker is copied along with it. " +
          "Give one of them its own key, e.g. .reusableComponent(\"MyName\"), leave the base unmarked and mark only the use sites that " +
          "should be referenced, or set OpenAPIDocsOptions.failOnDuplicateComponentName to false to disambiguate with a numeric suffix."
    ).map { case ((t, _), id) => t -> id }

    assigned.values.foreach(verifyComponentKey(_, section))
    assigned
  }

  private def verifyComponentKey(key: String, section: String): Unit =
    if (!key.matches("[a-zA-Z0-9.\\-_]+"))
      throw new IllegalStateException(
        s"$key is not a valid OpenAPI component key in components/$section; only letters, digits, '.', '-' and '_' are allowed. " +
          "Give the component an explicit key, e.g. .reusableComponent(\"MyName\")."
      )
}
