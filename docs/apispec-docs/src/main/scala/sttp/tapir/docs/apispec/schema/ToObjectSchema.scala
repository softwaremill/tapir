package sttp.tapir.docs.apispec.schema

import sttp.tapir.{Codec, Validator, Schema => TSchema, SchemaType => TSchemaType}

import scala.collection.mutable.ListBuffer

class ToObjectSchema(useRefForEnums: Boolean = false) {

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
      case TSchema(TSchemaType.SOption(o), _, _, _, _, _, _, _) =>
        apply(o)
      case s @ TSchema(st: TSchemaType.SProduct[_], _, _, _, _, _, _, _) =>
        productSchemas(s, st)
      case s @ TSchema(st: TSchemaType.SCoproduct[_], _, _, _, _, _, _, _) =>
        coproductSchemas(s, st)
      case s @ TSchema(st: TSchemaType.SOpenProduct[_, _], _, _, _, _, _, _, _) =>
        (st.info -> s: ObjectSchema) +: apply(st.valueSchema)
      case _ => List.empty
    }
  }

  private def productSchemas[T](s: TSchema[T], st: TSchemaType.SProduct[T]): List[ObjectSchema] = {
    (st.info -> s: ObjectSchema) +: st.fields
      .flatMap(a =>
        a.schema match {
          case s @ TSchema(_: TSchemaType.SString[_], _, _, _, _, _, _, _ @Validator.Enum(_, _, Some(info))) if useRefForEnums =>
            List(info -> s: ObjectSchema)
          case _ => apply(a.schema)
        }
      )
  }

  private def coproductSchemas[T](s: TSchema[T], st: TSchemaType.SCoproduct[T]): List[ObjectSchema] = {
    (st.info -> s: ObjectSchema) +: subtypesSchema(st)
      .flatMap(apply)
      .toList
  }

  private def fieldsSchema(p: TSchemaType.SProduct[_]): Seq[TSchema[_]] = p.fields.map(_.schema)

  private def subtypesSchema(st: TSchemaType.SCoproduct[_]): Seq[TSchema[_]] =
    st.subtypes.values.collect { case s @ TSchema(_: TSchemaType.SProduct[_], _, _, _, _, _, _, _) => s }.toSeq
}
