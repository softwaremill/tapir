package sttp.tapir.docs.apispec.schema

import sttp.tapir.SchemaType.SObjectInfo
import sttp.tapir.docs.apispec.ValidatorUtil.{elementValidator, fieldValidator}
import sttp.tapir.{Codec, Validator, Schema => TSchema, SchemaType => TSchemaType}

import scala.collection.mutable.ListBuffer

object ObjectTypeData {

  /** Keeps only the first object data for each `SObjectInfo`. In case of recursive objects, the first one is the
    * most complete as it contains the built-up structure, unlike subsequent ones, which only represent leaves (#354).
    */
  def unique(objs: Iterable[ObjectTypeData]): Iterable[ObjectTypeData] = {
    val seen: collection.mutable.Set[TSchemaType.SObjectInfo] = collection.mutable.Set()
    val result: ListBuffer[(TSchemaType.SObjectInfo, TypeData[_])] = ListBuffer()
    objs.foreach { obj =>
      if (!seen.contains(obj._1)) {
        seen.add(obj._1)
        result += obj
      }
    }
    result.toList
  }

  def apply[T](codec: Codec[_, T, _]): List[ObjectTypeData] = apply(TypeData(codec))

  def apply(typeData: TypeData[_]): List[ObjectTypeData] = {
    typeData match {
      case TypeData(TSchema(TSchemaType.SArray(o), _, _, _, _, _, _, schemaValidator), codecValidator) =>
        apply(TypeData(o, elementValidator(schemaValidator.asInstanceOf[Validator[Any]].and(codecValidator.asInstanceOf[Validator[Any]]))))
      case TypeData(s @ TSchema(st: TSchemaType.SProduct, _, _, _, _, _, _, schemaValidator), codecValidator) =>
        productSchemas(s, st, schemaValidator.asInstanceOf[Validator[Any]].and(codecValidator.asInstanceOf[Validator[Any]]))
      case TypeData(s @ TSchema(st: TSchemaType.SCoproduct, _, _, _, _, _, _, schemaValidator), codecValidator) =>
        coproductSchemas(s, st, schemaValidator.asInstanceOf[Validator[Any]].and(codecValidator.asInstanceOf[Validator[Any]]))
      case TypeData(s @ TSchema(st: TSchemaType.SOpenProduct, _, _, _, _, _, _, schemaValidator), codecValidator) =>
        (st.info -> TypeData(s, codecValidator): ObjectTypeData) +: apply(
          TypeData(
            st.valueSchema,
            elementValidator(schemaValidator.asInstanceOf[Validator[Any]].and(codecValidator.asInstanceOf[Validator[Any]]))
          )
        )
      case _ => List.empty
    }
  }

  private def productSchemas(s: TSchema[_], st: TSchemaType.SProduct, validator: Validator[_]): List[ObjectTypeData] = {
    (st.info -> TypeData(s, validator): ObjectTypeData) +: fieldsSchemaWithValidator(st, validator)
      .flatMap(apply)
      .toList
  }

  private def coproductSchemas(s: TSchema[_], st: TSchemaType.SCoproduct, validator: Validator[_]): List[ObjectTypeData] = {
    (st.info -> TypeData(s, validator): ObjectTypeData) +: subtypesSchemaWithValidator(st, validator)
      .flatMap(apply)
      .toList
  }

  private def fieldsSchemaWithValidator(p: TSchemaType.SProduct, v: Validator[_]): Seq[TypeData[_]] = {
    p.fields.map { f => TypeData(f._2, fieldValidator(v, f._1.name)) }.toList
  }

  private def subtypesSchemaWithValidator(st: TSchemaType.SCoproduct, v: Validator[_]): Seq[TypeData[_]] = {
    st.schemas.collect { case s @ TSchema(st: TSchemaType.SProduct, _, _, _, _, _, _, _) =>
      TypeData(s, subtypeValidator(v, st.info))
    }
  }

  private def subtypeValidator(v: Validator[_], subtype: SObjectInfo): Validator[_] =
    v match {
      case v @ Validator.Coproduct(_)                                  => v.subtypes.getOrElse(subtype.fullName, Validator.pass)
      case Validator.CollectionElements(v @ Validator.Coproduct(_), _) => v.subtypes.getOrElse(subtype.fullName, Validator.pass)
      case _                                                           => Validator.pass
    }
}
