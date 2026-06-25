package sttp.tapir.codegen.json

import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaArray,
  OpenapiSchemaBoolean,
  OpenapiSchemaEnum,
  OpenapiSchemaNumericType,
  OpenapiSchemaObject,
  OpenapiSchemaOneOf,
  OpenapiSchemaRef,
  OpenapiSchemaSimpleType,
  OpenapiSchemaString,
  OpenapiSchemaStringType
}

import scala.annotation.tailrec

object JsonHelpers {

  // Checks whether deserialization of this seq can be achieved just by iterating through the candidates.
  // Returns false if empty, true if valid, and throws if non-empty and containing issues.
  def checkForSoundness(name: String, allSchemas: Map[String, OpenapiSchemaType])(variants: Seq[OpenapiSchemaSimpleType]) = if (
    variants.size <= 1
  )
    false
  else {
    @tailrec def resolve(variant: OpenapiSchemaRef): OpenapiSchemaType = allSchemas(variant.stripped) match {
      case ref: OpenapiSchemaRef => resolve(ref)
      case resolved              => resolved
    }
    def maybeResolve(variant: OpenapiSchemaType): OpenapiSchemaType = variant match {
      case ref: OpenapiSchemaRef => resolve(ref)
      case other                 => other
    }
    def rCanLookLikeL(lhs: OpenapiSchemaType, rhs: OpenapiSchemaType): Boolean = (lhs, rhs) match {
      // check for equality first
      case (l, r) if l == r => true
      // then resolve any refs
      case (l: OpenapiSchemaRef, r) => rCanLookLikeL(resolve(l), r)
      case (l, r: OpenapiSchemaRef) => rCanLookLikeL(l, resolve(r))
      // nullable types can always look like each other since can be null
      case (l, r) if l.nullable && r.nullable => true
      // l enum can look like r enum if there's a value in common
      case (l: OpenapiSchemaEnum, r: OpenapiSchemaEnum) => l.items.map(_.value).toSet.intersect(r.items.map(_.value).toSet).nonEmpty
      // stringy subclasses of same type can look like each other
      case (l: OpenapiSchemaStringType, r: OpenapiSchemaStringType) if l.getClass == r.getClass => true
      // a string can look like any stringy type, and vice-versa
      case (_: OpenapiSchemaString, _: OpenapiSchemaStringType) | (_: OpenapiSchemaStringType, _: OpenapiSchemaString) => true
      // any numeric type can always look like any other (123 is valid for all subtypes, for example)
      case (_: OpenapiSchemaNumericType, _: OpenapiSchemaNumericType) => true
      // bools can always look like each other
      case (_: OpenapiSchemaBoolean, _: OpenapiSchemaBoolean) => true
      // arrays can always look like each other (if empty). We don't know about non-empty arrays yet.
      case (_: OpenapiSchemaArray, _: OpenapiSchemaArray) => true
      // objects need to recurse
      case (l: OpenapiSchemaObject, r: OpenapiSchemaObject) =>
        val requiredL =
          l.properties.filter(l.required contains _._1).filter { case (_, t) => t.default.isEmpty && !maybeResolve(t.`type`).nullable }
        val anyR = r.properties
        // if lhs has some required non-nullable fields with no default that rhs will never contain, then right cannot be mistaken for left
        if ((requiredL.keySet -- anyR.keySet).nonEmpty) false
        else {
          // otherwise, if any field on rhs required by lhs can't look like the similarly-named field on lhs, then r can't look like l
          val rForRequiredL = anyR.filter(requiredL.keySet contains _._1)
          requiredL.forall { case (k, lhsV) => rCanLookLikeL(lhsV.`type`, rForRequiredL(k).`type`) }
        }
      // Let's not support nested oneOfs for now, it's complex and I'm not sure if it's legal
      case (_: OpenapiSchemaOneOf, _) | (_, _: OpenapiSchemaOneOf) => throw new NotImplementedError("Not supported")
      // I think at this point we're ok
      case _ => false
    }
    val withAllSubsequent = variants.scanRight(Seq.empty[OpenapiSchemaSimpleType])(_ +: _).collect {
      case h +: t if t.nonEmpty => (h, t)
    }
    def getName(s: OpenapiSchemaSimpleType) = s match {
      case r: OpenapiSchemaRef => r.name
      case o                   => o.getClass.getSimpleName
    }
    val problems = withAllSubsequent
      .flatMap { case (variant, fallbacks) => fallbacks.filter(rCanLookLikeL(variant, _)).map(variant -> _) }
      .map { case (l, r) => s"${getName(l)} appears before ${getName(r)}, but a ${getName(r)} can be a valid ${getName(l)}" }
    if (problems.nonEmpty)
      throw new IllegalArgumentException(problems.mkString(s"Problems in non-discriminated oneOf '$name' declaration: (", "; ", ")"))
  }

  private[json] def inlineEndpointSchemas(doc: OpenapiDocument): Seq[(String, OpenapiSchemaType, Boolean)] =
    doc.paths.flatMap(p =>
      p.methods.flatMap(m =>
        m.responses
          .map(_.resolve(doc))
          .flatMap(_.content)
          .filter(o => o.contentType == "application/json" && o.schema.isInstanceOf[OpenapiSchemaObject])
          .map(c => (m.name(p.url).capitalize + "Response", c.schema, true)) ++
          m.requestBody.toSeq
            .map(_.resolve(doc))
            .flatMap(_.content)
            .filter(o => o.contentType == "application/json" && o.schema.isInstanceOf[OpenapiSchemaObject])
            .map(c => (m.name(p.url).capitalize + "Request", c.schema, true))
      )
    )
}
