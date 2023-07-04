package sttp.tapir.docs.apispec.schema

import sttp.tapir.{Codec, Schema => TSchema, SchemaType => TSchemaType}

import scala.collection.mutable.ListBuffer

private[docs] class ToKeyedSchemas {
  private[docs] def apply[T](codec: Codec[_, T, _]): List[KeyedSchema] = apply(codec.schema)

  private[docs] def apply(schema: TSchema[_]): List[KeyedSchema] = {
    val thisSchema = SchemaKey(schema).map(_ -> schema).toList
    val nestedSchemas = schema match {
      case TSchema(TSchemaType.SArray(o), _, _, _, _, _, _, _, _, _, _, _)            => apply(o)
      case t @ TSchema(o: TSchemaType.SOption[_, _], _, _, _, _, _, _, _, _, _, _, _) =>
        // #1168: if there's an optional field which is an object, with metadata defined (such as description), this
        // needs to be propagated to the target object, so that it isn't omitted.
        apply(propagateMetadataForOption(t, o).element)
      case TSchema(st: TSchemaType.SProduct[_], _, _, _, _, _, _, _, _, _, _, _)        => productSchemas(st)
      case TSchema(st: TSchemaType.SCoproduct[_], _, _, _, _, _, _, _, _, _, _, _)      => coproductSchemas(st)
      case TSchema(st: TSchemaType.SOpenProduct[_, _], _, _, _, _, _, _, _, _, _, _, _) => apply(st.valueSchema)
      case _                                                                            => List.empty
    }

    thisSchema ++ nestedSchemas
  }

  private def productSchemas[T](st: TSchemaType.SProduct[T]): List[KeyedSchema] = st.fields.flatMap(a => apply(a.schema))

  private def coproductSchemas[T](st: TSchemaType.SCoproduct[T]): List[KeyedSchema] = st.subtypes.flatMap(apply)
}

object ToKeyedSchemas {

  /** Keeps only the first object data for each [[SchemaKey]]. In case of recursive objects, the first one is the most complete as it
    * contains the built-up structure, unlike subsequent ones, which only represent leaves (#354, later extended for #2358, so that the
    * schemas have a secondary key - the product fields (if any)).
    */
  def unique(objs: Iterable[KeyedSchema]): Iterable[KeyedSchema] = {
    val seen: collection.mutable.Set[SchemaKey] = collection.mutable.Set()
    val result: ListBuffer[KeyedSchema] = ListBuffer()
    objs.foreach { obj =>
      if (!seen.contains(obj._1)) {
        seen.add(obj._1)
        result += obj
      }
    }
    result.toList
  }
}
