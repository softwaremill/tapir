package sttp.tapir.docs.apispec.schema

sealed trait MetaSchema {
  def schemaId: String
}

case object MetaSchema202012 extends MetaSchema {
  override lazy val schemaId: String = "https://json-schema.org/draft/2020-12/schema"
}
