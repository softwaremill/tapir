package sttp.tapir.docs.openapi

import sttp.apispec.openapi.{Header, Parameter}
import sttp.tapir.docs.apispec.schema.{TSchemaToASchema, calculateUniqueIds}
import sttp.tapir.{AnyEndpoint, EndpointTransput}
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
}

private[openapi] class ReusableComponentsForEndpoints(
    es: Iterable[AnyEndpoint],
    tschemaToASchema: TSchemaToASchema,
    failOnDuplicateComponentName: Boolean
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
        .flatMap { case (atom, parameter) => markerOf(atom).map(m => parameter -> m.name) }
    }

  private def collectMarkedHeaders(): Vector[((String, Header), Option[String])] =
    es.toVector.flatMap { e =>
      endpointToHeaders
        .withSourceAtoms(List(e.output, e.errorOutput))
        .toVector
        .flatMap { case (atom, nameAndHeader) => markerOf(atom).map(m => nameAndHeader -> m.name) }
    }

  private def markerOf(atom: EndpointTransput.Atom[_]): Option[ReusableComponent] =
    atom.attribute(ReusableComponentAttribute.reusableComponentAttributeKey)

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

    calculateUniqueIds[(T, Option[String])](
      distinctMarked,
      { case (t, explicitName) => explicitName.getOrElse(defaultName(t)) },
      failOnDuplicateComponentName,
      baseNames =>
        s"Duplicate OpenAPI component names found in components/$section: ${baseNames.mkString(", ")}. " +
          "Give one of them an explicit name, e.g. .reusableComponent(\"MyName\"), " +
          "or set OpenAPIDocsOptions.failOnDuplicateComponentName to false to disambiguate with a numeric suffix."
    ).map { case ((t, _), id) => t -> id }
  }
}
