package sttp.tapir.docs.apispec

import sttp.tapir.{AnyEndpoint, EndpointIO, EndpointInput}
import sttp.tapir.apispec.SecurityRequirement
import sttp.tapir.internal.{IterableToListMap, RichEndpoint, RichEndpointInput}

import scala.collection.immutable.{ListMap, ListSet}

private[docs] class SecurityRequirementsForEndpoints(securitySchemes: SecuritySchemes) {
  def apply(es: Iterable[AnyEndpoint]): List[SecurityRequirement] = ListSet(es.toList.flatMap(apply): _*).toList

  def apply(e: AnyEndpoint): List[SecurityRequirement] = {
    val auths = e.auths
    val nonEmptyAuths = auths.filterNot(_.isInputEmpty)
    // an emptyAuth is used as a marker that authentication is optional
    val hasEmptyAuth = auths.size != nonEmptyAuths.size

    // * auths in a single group always form a single security requirement
    // * multiple groups form alternate security requirements
    // * optional auths without a security requirement become alternate requirements
    val requirements: List[SecurityRequirement] = nonEmptyAuths.groupBy(_.info.group).toList.sortBy(_._1.getOrElse("")).flatMap {
      case (None, noGroupAuths) =>
        if (authOptional(noGroupAuths)) {
          // all optional, creating separate security requirements
          noGroupAuths.map(a => securityRequirement(List(a))).toList
        } else List(securityRequirement(noGroupAuths))
      case (Some(_), groupAuths) => List(securityRequirement(groupAuths))
    }

    // auth is also optional if there's a single requirement, where all the inputs are optional
    val securityOptional = hasEmptyAuth || (authOptional(auths) && requirements.size <= 1)

    if (requirements.isEmpty) List.empty
    else {
      if (securityOptional) {
        (ListMap.empty: SecurityRequirement) :: requirements
      } else {
        requirements
      }
    }
  }

  private def securityRequirement(auths: Seq[EndpointInput.Auth[_, _ <: EndpointInput.AuthType]]): SecurityRequirement = auths.flatMap {
    case auth @ EndpointInput.Auth(_, _, _, info: EndpointInput.AuthType.ScopedOAuth2, _) =>
      securitySchemes.get(auth).map(_._1).map((_, info.requiredScopes.toVector))
    case auth => securitySchemes.get(auth).map(_._1).map((_, Vector.empty))
  }.toListMap

  private def authOptional(auths: Seq[EndpointInput.Auth[_, _ <: EndpointInput.AuthType]]): Boolean =
    auths.flatMap(_.asVectorOfBasicInputs()).forall {
      case i: EndpointInput.Atom[_]          => i.codec.schema.isOptional
      case EndpointIO.OneOfBody(variants, _) => variants.forall(_.body.codec.schema.isOptional)
    }
}
