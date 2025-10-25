package sttp.tapir.codegen.util

import sttp.tapir.codegen.openapi.models.OpenapiSchemaType
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaAllOf,
  OpenapiSchemaAnyOf,
  OpenapiSchemaArray,
  OpenapiSchemaField,
  OpenapiSchemaMap,
  OpenapiSchemaNot,
  OpenapiSchemaObject,
  OpenapiSchemaOneOf,
  OpenapiSchemaRef
}

import scala.annotation.tailrec

object DocUtils {
  @tailrec
  final def recursiveFindAllReferencedSchemaTypes(
      allSchemas: Map[String, OpenapiSchemaType]
  )(toCheck: OpenapiSchemaType, checked: Set[String], tail: Seq[OpenapiSchemaType]): Set[String] = {
    def nextParamsFromTypeSeq(types: Seq[OpenapiSchemaType]) = types match {
      case Nil          => None
      case next +: rest => Some((next, checked, rest ++ tail))
    }
    val maybeNextParams = toCheck match {
      case ref: OpenapiSchemaRef if ref.isSchema =>
        val name = ref.stripped
        val maybeAppended = if (checked contains name) None else allSchemas.get(name)
        (tail ++ maybeAppended) match {
          case Nil          => None
          case next +: rest => Some((next, checked + name, rest))
        }
      case OpenapiSchemaArray(items, _, _, _)                             => Some((items, checked, tail))
      case OpenapiSchemaNot(items)                                        => Some((items, checked, tail))
      case OpenapiSchemaMap(items, _, _)                                  => Some((items, checked, tail))
      case OpenapiSchemaOneOf(types, _)                                   => nextParamsFromTypeSeq(types)
      case OpenapiSchemaAnyOf(types)                                      => nextParamsFromTypeSeq(types)
      case OpenapiSchemaAllOf(types)                                      => nextParamsFromTypeSeq(types)
      case OpenapiSchemaObject(properties, _, _, _) if properties.isEmpty => None
      case OpenapiSchemaObject(properties, required, nullable, _)         =>
        val propToCheck = properties.head
        val (propToCheckName, OpenapiSchemaField(propToCheckType, _, _)) = propToCheck
        val objectWithoutHeadField = OpenapiSchemaObject(properties - propToCheckName, required, nullable)
        Some((propToCheckType, checked, objectWithoutHeadField +: tail))
      case _ => None
    }
    maybeNextParams match {
      case None =>
        tail match {
          case Nil          => checked
          case next +: rest => recursiveFindAllReferencedSchemaTypes(allSchemas)(next, checked, rest)
        }
      case Some((next, checked, rest)) => recursiveFindAllReferencedSchemaTypes(allSchemas)(next, checked, rest)
    }
  }
}
