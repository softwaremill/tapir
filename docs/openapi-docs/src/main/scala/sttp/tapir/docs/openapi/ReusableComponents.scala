package sttp.tapir.docs.openapi

import sttp.apispec.openapi.{Header, Parameter}
import sttp.tapir.docs.apispec.schema.{TSchemaToASchema, calculateUniqueIds}
import sttp.tapir.{AnyEndpoint, EndpointTransput}
import sttp.tapir.internal._

/** The parameters and response headers that have been marked with `.reusableComponent`, each mapped to the key it is emitted under in the
  * `components` section.
  *
  * Both maps are keyed by the **generated value**, not by the source input. `EndpointInput.Query` equality includes its `Codec`, and codec
  * instances are not reliably equal across separate definitions, so keying by the input would be fragile; `Parameter` and `Header` are
  * plain data, so structural equality is dependable — `EndpointToOpenAPIPaths` already relies on exactly this via its `.distinct`.
  *
  * The deliberate consequence: once any use site is marked, every structurally identical parameter is referenced, marked or not. That is
  * the behaviour the issue asks for — mark the shared `val` once and it applies everywhere.
  *
  * A response header's name lives in its map key rather than in the `Header` itself, hence `(String, Header)`.
  */
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

/** Runs before path building and finds every atom marked with `.reusableComponent`.
  *
  * A pre-pass is mandatory rather than stylistic: `EndpointToOpenAPIDocs` builds the components section before it builds the paths, so
  * components cannot be discovered as a side effect of path building.
  */
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

  /** Response headers are collected without filtering hidden outputs, and without walking `defaultDecodeFailureOutput`: `collectHeaders`
    * never filtered hidden outputs, and filtering here would either create a component nothing references or drop one that is still
    * emitted; the synthesised decode-failure outputs are a status code plus a string body, so they contain no headers.
    *
    * `components/parameters` and `components/headers` are independent namespaces, which is why `assignNames` is called once per section: a
    * `tenantId` in each does not collide.
    */
  private def collectMarkedHeaders(): Vector[((String, Header), Option[String])] =
    es.toVector.flatMap { e =>
      endpointToHeaders
        .withSourceAtoms(List(e.output, e.errorOutput))
        .toVector
        .flatMap { case (atom, nameAndHeader) => markerOf(atom).map(m => nameAndHeader -> m.name) }
    }

  // read the attribute directly rather than through ReusableComponentAttribute's implicit class: the atom's static type here is
  // existential, which the implicit class's `E <: EndpointTransput.Atom[_]` parameter does not reliably match
  private def markerOf(atom: EndpointTransput.Atom[_]): Option[ReusableComponent] =
    atom.attribute(ReusableComponentAttribute.reusableComponentAttributeKey)

  /** Assigns a unique component key to each distinct marked value: the marker's explicit name when given, otherwise `defaultName`, with
    * `calculateUniqueIds` suffixing on collision — or failing, when `failOnDuplicateComponentName`.
    */
  private def assignNames[T](marked: Vector[(T, Option[String])], defaultName: T => String, section: String): Map[T, String] = {
    // A value may be marked at several use sites. Collapse them, taking the first explicit name, so the result does not depend on how
    // many endpoints happen to reuse it. Sort afterwards: `groupBy` yields an unordered Map, and id suffixing is order-dependent.
    val distinctMarked: Vector[(T, Option[String])] = marked
      .groupBy(_._1)
      .toVector
      .map { case (t, markers) => t -> markers.flatMap(_._2).headOption }
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
