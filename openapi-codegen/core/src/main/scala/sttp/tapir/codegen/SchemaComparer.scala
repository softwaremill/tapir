package sttp.tapir.codegen

import sttp.tapir.codegen.openapi.models.OpenapiSchemaType
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  Discriminator,
  OpenapiSchemaAllOf,
  OpenapiSchemaAny,
  OpenapiSchemaAnyOf,
  OpenapiSchemaArray,
  OpenapiSchemaBoolean,
  OpenapiSchemaByte,
  OpenapiSchemaConstantString,
  OpenapiSchemaDate,
  OpenapiSchemaDateTime,
  OpenapiSchemaDouble,
  OpenapiSchemaDuration,
  OpenapiSchemaEnum,
  OpenapiSchemaField,
  OpenapiSchemaFloat,
  OpenapiSchemaInt,
  OpenapiSchemaLong,
  OpenapiSchemaMap,
  OpenapiSchemaNot,
  OpenapiSchemaObject,
  OpenapiSchemaOneOf,
  OpenapiSchemaRef,
  OpenapiSchemaSimpleType,
  OpenapiSchemaString,
  OpenapiSchemaUUID
}

object SchemaComparer {

  /** Returns schema names present in both maps whose definitions are structurally identical (including transitive refs). */
  def findIdenticalSchemaNames(
      current: Map[String, OpenapiSchemaType],
      dependency: Map[String, OpenapiSchemaType]
  ): Set[String] =
    // TODO: There's an issue here; if we have obj A, obj B, C oneOf [A, B], and A(1) != A(2), then we'll not redeclare B, even though we must. 
    current.keySet.intersect(dependency.keySet).filter { name =>
      schemasEqual(name, current(name), current, name, dependency(name), dependency, Set.empty)
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
      case (l: OpenapiSchemaString, r: OpenapiSchemaString) =>
        l.pattern == r.pattern && l.minLength == r.minLength && l.maxLength == r.maxLength
      case (_: OpenapiSchemaDate, _: OpenapiSchemaDate)           => true
      case (_: OpenapiSchemaDateTime, _: OpenapiSchemaDateTime)   => true
      case (_: OpenapiSchemaDuration, _: OpenapiSchemaDuration)   => true
      case (_: OpenapiSchemaByte, _: OpenapiSchemaByte)           => true
      case (_: OpenapiSchemaUUID, _: OpenapiSchemaUUID)           => true
      case (l: OpenapiSchemaDouble, r: OpenapiSchemaDouble)       => l.restrictions == r.restrictions
      case (l: OpenapiSchemaFloat, r: OpenapiSchemaFloat)         => l.restrictions == r.restrictions
      case (l: OpenapiSchemaInt, r: OpenapiSchemaInt)             => l.restrictions == r.restrictions
      case (l: OpenapiSchemaLong, r: OpenapiSchemaLong)           => l.restrictions == r.restrictions
      case (l: OpenapiSchemaEnum, r: OpenapiSchemaEnum) =>
        l.`type` == r.`type` && l.items.map(_.value) == r.items.map(_.value)
      case (l: OpenapiSchemaConstantString, r: OpenapiSchemaConstantString) => l.value == r.value
      case (l: OpenapiSchemaArray, r: OpenapiSchemaArray) =>
        l.restrictions.minItems == r.restrictions.minItems &&
        l.restrictions.maxItems == r.restrictions.maxItems &&
        l.restrictions.uniqueItems == r.restrictions.uniqueItems &&
        schemasEqual(leftName, l.items, leftAll, rightName, r.items, rightAll, nextVisited)
      case (l: OpenapiSchemaMap, r: OpenapiSchemaMap) =>
        l.restrictions == r.restrictions &&
        schemasEqual(leftName, l.items, leftAll, rightName, r.items, rightAll, nextVisited)
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
          simpleTypesEqual(leftName, lt, leftAll, rightName, rt, rightAll, nextVisited)
        }
      case (l: OpenapiSchemaAnyOf, r: OpenapiSchemaAnyOf) =>
        l.types.size == r.types.size &&
        l.types.zip(r.types).forall { case (lt, rt) =>
          simpleTypesEqual(leftName, lt, leftAll, rightName, rt, rightAll, nextVisited)
        }
      case (l: OpenapiSchemaAllOf, r: OpenapiSchemaAllOf) =>
        l.types.size == r.types.size &&
        l.types.zip(r.types).forall { case (lt, rt) =>
          schemasEqual(leftName, lt, leftAll, rightName, rt, rightAll, nextVisited)
        }
      case (l: OpenapiSchemaNot, r: OpenapiSchemaNot) =>
        schemasEqual(leftName, l.`type`, leftAll, rightName, r.`type`, rightAll, nextVisited)
      case (l: OpenapiSchemaAny, r: OpenapiSchemaAny) => l.tpe == r.tpe
      case _                                            => false
    }
  }

  private def simpleTypesEqual(
      leftName: String,
      left: OpenapiSchemaSimpleType,
      leftAll: Map[String, OpenapiSchemaType],
      rightName: String,
      right: OpenapiSchemaSimpleType,
      rightAll: Map[String, OpenapiSchemaType],
      visited: Set[(String, String)]
  ): Boolean =
    schemasEqual(leftName, left, leftAll, rightName, right, rightAll, visited)

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
