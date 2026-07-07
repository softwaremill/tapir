package sttp.tapir.codegen.dedup

import sttp.tapir.codegen.openapi.models.OpenapiModels._
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType._
import sttp.tapir.codegen.openapi.models.{OpenapiModels, OpenapiSchemaType}

object SchemaComparer {
  private def resolved(doc: OpenapiDocument, ps: Map[String, OpenapiModels.OpenapiParameter])(
      p: OpenapiPath
  ): Seq[(String, OpenapiPathMethod)] = p.methods.map(m =>
    m.name(p.url) -> m.copy(
      requestBody = m.requestBody.map(_.resolve(doc)),
      responses = m.responses.map(_.resolve(doc)),
      parameters = m.parameters.map(_.toResolved(ps)),
      security = Some(m.security.getOrElse(doc.security))
    )
  )
  private def securityMatches(current: OpenapiDocument, dependency: OpenapiDocument)(m: Seq[Map[String, Seq[String]]]): Boolean = {
    m.flatMap(_.keySet).toSet.forall { s =>
      val depDefn = dependency.components.flatMap(_.securitySchemes.get(s))
      val currDefn = current.components.flatMap(_.securitySchemes.get(s))
      depDefn == currDefn
    }
  }

  def findReusedEndpointNames(
      current: OpenapiDocument,
      dependency: OpenapiDocument,
      currentSchemas: Map[String, OpenapiSchemaType],
      dependencySchemas: Map[String, OpenapiSchemaType]
  ): Set[String] = {
    val depsPs = Option(dependency.components).flatten.map(_.parameters).getOrElse(Map.empty)
    val resolvedDeps = dependency.paths.flatMap(resolved(dependency, depsPs)).groupBy(_._1).map { case (k, v) => k -> v.head._2 }
    val currPs = Option(current.components).flatten.map(_.parameters).getOrElse(Map.empty)
    val resolvedCurr = current.paths.flatMap(resolved(current, currPs)).groupBy(_._1).map { case (k, v) => k -> v.head._2 }
    val mayMatch = resolvedCurr.filter { case (name, m) => resolvedDeps.get(name).contains(m) }

    mayMatch.filter { case (name, m) =>
      m.requestBody.toSeq
        .flatMap(_.asInstanceOf[OpenapiRequestBodyDefn].content.map(_.schema))
        .forall(t => schemasEqual(name, t, currentSchemas, name, t, dependencySchemas, Set.empty)) &&
      m.responses.toSeq
        .flatMap(_.asInstanceOf[OpenapiResponseDef].content.map(_.schema))
        .forall(t => schemasEqual(name, t, currentSchemas, name, t, dependencySchemas, Set.empty)) &&
      m.parameters
        .map(_.resolve(currPs).schema)
        .forall(t => schemasEqual(name, t, currentSchemas, name, t, dependencySchemas, Set.empty)) &&
      securityMatches(current, dependency)(m.security.get)
    }.keySet
  }

  /** Returns schema names present in both maps whose definitions are structurally identical (including transitive refs). */
  def findIdenticalSchemaNames(
      current: Map[String, OpenapiSchemaType],
      dependency: Map[String, OpenapiSchemaType]
  ): Set[String] = {
    val commonNames = current.keySet.intersect(dependency.keySet)
    val equalFirstPass = commonNames.filter { name =>
      schemasEqual(name, current(name), current, name, dependency(name), dependency, Set.empty)
    }
    // need to re-define A for any A extends T, B extends T, if B differs between versions
    // Even if A doesn't differ, we have sealed trait inheritance.
    val neqOneOfs = current
      .collect {
        case (name, s: OpenapiSchemaOneOf) if !equalFirstPass.contains(name) => s.types
        case (name, s: OpenapiSchemaAnyOf) if !equalFirstPass.contains(name) => s.types
      }
      .flatten
      .collect { case s: OpenapiSchemaRef => s.stripped }
      .toSet
    equalFirstPass -- neqOneOfs
  }

  private def schemasEqual(
      leftName: String,
      left: OpenapiSchemaType,
      leftAll: Map[String, OpenapiSchemaType],
      rightName: String,
      right: OpenapiSchemaType,
      rightAll: Map[String, OpenapiSchemaType],
      visited: Set[(String, String)]
  ): Boolean = {
    if (left.nullable != right.nullable) return false
    if (visited.contains(leftName -> rightName)) return true
    val nextVisited = visited + (leftName -> rightName)
    (left, right) match {
      case (l: OpenapiSchemaRef, r: OpenapiSchemaRef) =>
        val s1 = l.stripped
        val s2 = r.stripped
        s1 == s2 && leftAll.get(s1).zip(rightAll.get(s2)).exists { case (ls, rs) =>
          schemasEqual(s1, ls, leftAll, s2, rs, rightAll, nextVisited)
        }
      case (_: OpenapiSchemaBoolean, _: OpenapiSchemaBoolean) => true
      case (l: OpenapiSchemaString, r: OpenapiSchemaString)   =>
        l.pattern == r.pattern && l.minLength == r.minLength && l.maxLength == r.maxLength
      case (_: OpenapiSchemaDate, _: OpenapiSchemaDate)         => true
      case (_: OpenapiSchemaDateTime, _: OpenapiSchemaDateTime) => true
      case (_: OpenapiSchemaDuration, _: OpenapiSchemaDuration) => true
      case (_: OpenapiSchemaByte, _: OpenapiSchemaByte)         => true
      case (_: OpenapiSchemaUUID, _: OpenapiSchemaUUID)         => true
      case (l: OpenapiSchemaDouble, r: OpenapiSchemaDouble)     => l.restrictions == r.restrictions
      case (l: OpenapiSchemaFloat, r: OpenapiSchemaFloat)       => l.restrictions == r.restrictions
      case (l: OpenapiSchemaInt, r: OpenapiSchemaInt)           => l.restrictions == r.restrictions
      case (l: OpenapiSchemaLong, r: OpenapiSchemaLong)         => l.restrictions == r.restrictions
      case (l: OpenapiSchemaEnum, r: OpenapiSchemaEnum)         =>
        l.`type` == r.`type` && l.items.map(_.value) == r.items.map(_.value)
      case (l: OpenapiSchemaConstantString, r: OpenapiSchemaConstantString) => l.value == r.value
      case (l: OpenapiSchemaArray, r: OpenapiSchemaArray)                   =>
        l.restrictions.minItems == r.restrictions.minItems &&
        l.restrictions.maxItems == r.restrictions.maxItems &&
        l.restrictions.uniqueItems == r.restrictions.uniqueItems &&
        schemasEqual(leftName + "Item", l.items, leftAll, rightName + "Item", r.items, rightAll, nextVisited)
      case (l: OpenapiSchemaMap, r: OpenapiSchemaMap) =>
        l.restrictions == r.restrictions &&
        schemasEqual(leftName + "Item", l.items, leftAll, rightName + "Item", r.items, rightAll, nextVisited)
      case (l: OpenapiSchemaObject, r: OpenapiSchemaObject) =>
        l.required.toSet == r.required.toSet &&
        l.properties.size == r.properties.size &&
        l.properties.keys.toSet == r.properties.keys.toSet &&
        l.properties.forall { case (k, lf) =>
          r.properties.get(k).exists(rf => fieldsEqual(k, lf, rf, leftAll, rightAll, nextVisited))
        }
      case (l: OpenapiSchemaOneOf, r: OpenapiSchemaOneOf) =>
        discriminatorsEqual(l.discriminator, r.discriminator) &&
        l.types.size == r.types.size &&
        l.types.zip(r.types).forall { case (lt, rt) =>
          schemasEqual(leftName + "Variant", lt, leftAll, rightName + "Variant", rt, rightAll, nextVisited)
        }
      case (l: OpenapiSchemaAnyOf, r: OpenapiSchemaAnyOf) =>
        l.types.size == r.types.size &&
        l.types.zip(r.types).forall { case (lt, rt) =>
          schemasEqual(leftName + "Variant", lt, leftAll, rightName + "Variant", rt, rightAll, nextVisited)
        }
      case (l: OpenapiSchemaAllOf, r: OpenapiSchemaAllOf) =>
        l.types.size == r.types.size &&
        l.types.zip(r.types).forall { case (lt, rt) =>
          schemasEqual(leftName + "Trait", lt, leftAll, rightName + "Trait", rt, rightAll, nextVisited)
        }
      case (l: OpenapiSchemaNot, r: OpenapiSchemaNot) =>
        schemasEqual(leftName + "Not", l.`type`, leftAll, rightName + "Not", r.`type`, rightAll, nextVisited)
      case (l: OpenapiSchemaAny, r: OpenapiSchemaAny) => l.tpe == r.tpe
      case _                                          => false
    }
  }

  private def fieldsEqual(
      fieldName: String,
      left: OpenapiSchemaField,
      right: OpenapiSchemaField,
      leftAll: Map[String, OpenapiSchemaType],
      rightAll: Map[String, OpenapiSchemaType],
      visited: Set[(String, String)]
  ): Boolean =
    left.default == right.default &&
      schemasEqual(fieldName, left.`type`, leftAll, fieldName, right.`type`, rightAll, visited)

  private def discriminatorsEqual(left: Option[Discriminator], right: Option[Discriminator]): Boolean =
    (left, right) match {
      case (None, None)       => true
      case (Some(l), Some(r)) => l.propertyName == r.propertyName && l.mapping == r.mapping
      case _                  => false
    }
}
