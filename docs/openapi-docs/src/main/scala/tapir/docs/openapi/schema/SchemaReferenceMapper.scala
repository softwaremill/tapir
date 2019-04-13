package tapir.docs.openapi.schema

import tapir.openapi.Reference

class SchemaReferenceMapper(fullNameToKey: Map[String, SchemaKey]) {

  def map(fullName: String): Reference = {
    Reference("#/components/schemas/" + fullNameToKey(fullName))
  }
}
