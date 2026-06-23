package sttp.tapir.codegen

import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument

/** Describes a dependency package whose models may be reused via type aliases. */
case class PackageReuseContext(
    reusedSchemas: Set[String],
    dependencyModelPath: String,
    dependencyObjectName: String,
    dependencyMeta: GenerationMeta,
    replacedSchemas: Set[String],
    reusedEndpointNames: Set[String]
) {
  lazy val depPkg: String = dependencyModelPath.split('.').dropRight(1).mkString(".")
}

object PackageReuseContext {
  val none: PackageReuseContext = PackageReuseContext(Set.empty, "", "", GenerationMeta.default, Set.empty, Set.empty)

  def fromDocuments(
      current: OpenapiDocument,
      dependency: OpenapiDocument,
      dependencyPackage: String,
      dependencyObjectName: String,
      dependencyMeta: GenerationMeta
  ): PackageReuseContext = {
    val currentSchemas = current.components.toSeq.flatMap(_.schemas).toMap
    val dependencySchemas = dependency.components.toSeq.flatMap(_.schemas).toMap
    val reused = SchemaComparer.findIdenticalSchemaNames(currentSchemas, dependencySchemas)
    val replaced = currentSchemas.keySet.intersect(dependencySchemas.keySet) -- reused
    val reusedEndpointNames = SchemaComparer.findReusedEndpointNames(current, dependency, currentSchemas, dependencySchemas)
    PackageReuseContext(
      reused,
      s"$dependencyPackage.$dependencyObjectName",
      dependencyObjectName,
      dependencyMeta,
      replaced,
      reusedEndpointNames
    )
  }

  def aliasType(name: String, ctx: PackageReuseContext): String =
    s"type $name = ${ctx.dependencyModelPath}.$name"
  def enumAliasType(name: String, ctx: PackageReuseContext): String =
    s"""type $name = ${ctx.dependencyModelPath}.$name
       |val $name = ${ctx.dependencyModelPath}.$name""".stripMargin

  def isReusedSchema(name: String, ctx: PackageReuseContext): Boolean = ctx.reusedSchemas.contains(name)
}
