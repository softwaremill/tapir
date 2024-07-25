package sttp.tapir.docs.apispec.schema

import sttp.tapir.Schema.Title
import sttp.tapir.{Codec, Schema => TSchema, SchemaType => TSchemaType}

private[docs] object ToKeyedSchemas {
  def apply[T](codec: Codec[_, T, _]): List[KeyedSchema] = apply(codec.schema)

  def apply(schema: TSchema[_]): List[KeyedSchema] = {
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
      case _                                                                         => List.empty
    }

    thisSchema ++ nestedSchemas
  }

  private def productSchemas[T](st: TSchemaType.SProduct[T]): List[KeyedSchema] = st.fields.flatMap(a => apply(a.schema))

  private def coproductSchemas[T](st: TSchemaType.SCoproduct[T]): List[KeyedSchema] = st.subtypes.flatMap(apply)

  /** Keeps only the first object data for each [[SchemaKey]]. In case of recursive objects, the first one is the most complete as it
    * contains the built-up structure, unlike subsequent ones, which only represent leaves (#354, later extended for #2358, so that the
    * schemas have a secondary key - the product fields (if any)).
    *
    * There might also be multiple copies of the same schema due to independent usage-site customisations (e.g. description). In this case,
    * we combine the schemas, reverting all per-usage customisable properties to their default values. These properties should be added when
    * creating a reference schema (#1203).
    */
  def uniqueCombined(objs: Iterable[KeyedSchema]): Iterable[KeyedSchema] = {
    val grouped = objs.groupBy(_._1)

    // taking care to maintain the original order of keys in objs
    objs.map(_._1).toList.distinct.map { key =>
      (key, grouped(key).map(_._2).reduce(combine))
    }
  }

  /** Combines the two schemas, reverting all per-usage customisable properties to their default values, if their values diverge. */
  private def combine(s1: TSchema[_], s2: TSchema[_]): TSchema[_] = {
    var result = s1
    if (s1.description != s2.description) result = result.copy(description = None)
    if (s1.default != s2.default) result = result.copy(default = None)
    if (s1.encodedExample != s2.encodedExample) result = result.copy(encodedExample = None)
    if (s1.deprecated != s2.deprecated) result = result.deprecated(false)
    if (s1.attributes.get(Title.Attribute) != s2.attributes.get(Title.Attribute))
      result = result.copy(attributes = result.attributes.remove(Title.Attribute))
    result
  }
}
