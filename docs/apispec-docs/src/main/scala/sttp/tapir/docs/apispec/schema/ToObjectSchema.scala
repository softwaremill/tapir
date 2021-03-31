package sttp.tapir.docs.apispec.schema

import sttp.tapir.SchemaType.SObjectInfo
import sttp.tapir.{Codec, Validator, Schema => TSchema, SchemaType => TSchemaType}

import scala.collection.mutable.ListBuffer

class ToObjectSchema(val enumsToComponent: Boolean = false) {

  /** Keeps only the first object data for each `SObjectInfo`. In case of recursive objects, the first one is the
    * most complete as it contains the built-up structure, unlike subsequent ones, which only represent leaves (#354).
    */
  def unique(objs: Iterable[ObjectSchema]): Iterable[ObjectSchema] = {
    val seen: collection.mutable.Set[TSchemaType.SObjectInfo] = collection.mutable.Set()
    val result: ListBuffer[(TSchemaType.SObjectInfo, TSchema[_])] = ListBuffer()
    objs.foreach { obj =>
      if (!seen.contains(obj._1)) {
        seen.add(obj._1)
        result += obj
      }
    }
    result.toList
  }

  def apply[T](codec: Codec[_, T, _]): List[ObjectSchema] = apply(codec.schema)

  def apply(typeData: TSchema[_]): List[ObjectSchema] = {
    typeData match {
      case TSchema(TSchemaType.SArray(o), _, _, _, _, _, _, _) =>
        apply(o)
      case s @ TSchema(st: TSchemaType.SProduct, _, _, _, _, _, _, _) =>
        productSchemas(s, st)
      case s @ TSchema(st: TSchemaType.SCoproduct, _, _, _, _, _, _, _) =>
        coproductSchemas(s, st)
      case s @ TSchema(st: TSchemaType.SOpenProduct, _, _, _, _, _, _, _) =>
        (st.info -> s: ObjectSchema) +: apply(st.valueSchema)
      case _ => List.empty
    }
  }

  private def productSchemas(s: TSchema[_], st: TSchemaType.SProduct): List[ObjectSchema] = {
    (st.info -> s: ObjectSchema) +: st.fields
      .flatMap(a =>
        a match {
          case (name, s @ TSchema(_: TSchemaType.SString.type, _, _, _, _, _, _, _: Validator.Enum[_])) if enumsToComponent =>
            List(SObjectInfo(name.name.capitalize) -> s: ObjectSchema)
          case (_, schema) => apply(schema)
        }
      )
      .toList
  }

  private def coproductSchemas(s: TSchema[_], st: TSchemaType.SCoproduct): List[ObjectSchema] = {
    (st.info -> s: ObjectSchema) +: subtypesSchema(st)
      .flatMap(apply)
      .toList
  }

  private def subtypesSchema(st: TSchemaType.SCoproduct): Seq[TSchema[_]] =
    st.schemas.collect { case s @ TSchema(_: TSchemaType.SProduct, _, _, _, _, _, _, _) => s }
}
