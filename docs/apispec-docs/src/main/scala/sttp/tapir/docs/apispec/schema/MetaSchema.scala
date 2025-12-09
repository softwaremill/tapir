package sttp.tapir.docs.apispec.schema

sealed trait MetaSchema {
  def schemaId: String
}

case object MetaSchemaDraft04 extends MetaSchema {
  override lazy val schemaId: String = "http://json-schema.org/draft-04/schema#"
}

case object MetaSchemaDraft202012 extends MetaSchema {
  override lazy val schemaId: String = "https://json-schema.org/draft/2020-12/schema"
}
