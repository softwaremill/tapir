package sttp.tapir.codegen.dedup

import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.dedup.PackageReuseContext
import sttp.tapir.codegen.security.SecurityWrapperDefn

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
  def modelRoot(seperateFilesForModels: Boolean) = if (seperateFilesForModels) s"$depPkg.models" else dependencyModelPath
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

  def aliasType(name: String, ctx: PackageReuseContext, separateFilesForModels: Boolean): String =
    s"type $name = ${ctx.modelRoot(separateFilesForModels)}.$name"
  def enumAliasType(name: String, ctx: PackageReuseContext, separateFilesForModels: Boolean): String = {
    val parentPkg = ctx.modelRoot(separateFilesForModels )
    s"""type $name = $parentPkg.$name
       |val $name = $parentPkg.$name""".stripMargin
  }

  def isReusedSchema(name: String, ctx: PackageReuseContext): Boolean = ctx.reusedSchemas.contains(name)
}

object GenerationMeta {
  val default: GenerationMeta =
    GenerationMeta(Seq("TapirGeneratedEndpointsSchemas"), false, false, Nil, Set.empty, Nil, 0, Set.empty, Set.empty, Set.empty)
}
case class GenerationMeta(
    schemaFiles: Seq[String],
    hasValidators: Boolean,
    schemasContainAny: Boolean,
    explicitNonObjTypes: Seq[String],
    security: Set[SecurityWrapperDefn],
    extensions: Seq[(String, String, String)],
    schemaObjectCount: Int,
    allTransitiveJsonParamRefs: Set[String],
    jsonParamRefs: Set[String],
    aliasedNames: Set[String]
) {
  def partition(securityWrappers: Set[SecurityWrapperDefn]): (Set[SecurityWrapperDefn], Set[SecurityWrapperDefn]) = {
    val changed = scala.collection.mutable.Set.empty[SecurityWrapperDefn]
    val m = scala.collection.mutable.Set.empty[SecurityWrapperDefn]
    val ct = scala.collection.mutable.Set.empty[String]
    securityWrappers.foreach(w =>
      if (!security.contains(w)) {
        changed += w
        w.schemas.map(_.typeName).foreach(t => ct += t)
      } else m += w
    )
    def checkChanged: Unit = {
      var changedCount = 0
      val mSnapshot = m.toSet
      mSnapshot.foreach(w =>
        if (w.schemas.map(_.typeName).exists(ct.contains)) {
          changedCount += 1
          w.schemas.map(_.typeName).foreach(t => ct += t)
          changed += w
          m.remove(w)
        }
      )
      if (changedCount != 0) checkChanged
    }
    checkChanged
    m.toSet -> changed.toSet
  }
}
