lazy val root = (project in file("."))
  .enablePlugins(OpenapiCodegenPlugin)
  .settings(
    scalaVersion := "2.13.18",
    version := "0.1",
    openapiGenerateEndpointTypes := true,
    openapiSwaggerFile := baseDirectory.value / "specs" / "core.yaml",
    openapiPackage := "sttp.tapir.generated.core",
    openapiAdditionalPackages := {
      val specs = baseDirectory.value / "specs"
      List(
        // original v1/v2 versioned API (v1 depends on v2, not core)
        "sttp.tapir.generated.v2" -> (specs / "v2.yaml"),
        "sttp.tapir.generated.v1" -> (specs / "v1.yaml"),
        // full reuse
        "sttp.tapir.generated.reuse.all" -> (specs / "reuse_all.yaml"),
        "sttp.tapir.generated.reuse.enum" -> (specs / "reuse_enum.yaml"),
        "sttp.tapir.generated.reuse.nested" -> (specs / "reuse_nested.yaml"),
        "sttp.tapir.generated.reuse.oneof" -> (specs / "reuse_oneof.yaml"),
        "sttp.tapir.generated.reuse.map" -> (specs / "reuse_map.yaml"),
        "sttp.tapir.generated.reuse.array" -> (specs / "reuse_array.yaml"),
        "sttp.tapir.generated.reuse.alias" -> (specs / "reuse_simple_alias.yaml"),
        "sttp.tapir.generated.reuse.allof_pet" -> (specs / "reuse_allof_pet.yaml"),
        "sttp.tapir.generated.transitive.reuse" -> (specs / "transitive_reuse.yaml"),
        // redefines (should not alias)
        "sttp.tapir.generated.redefine.pet_extra" -> (specs / "redefine_pet_extra_field.yaml"),
        "sttp.tapir.generated.redefine.pet_required" -> (specs / "redefine_pet_required.yaml"),
        "sttp.tapir.generated.redefine.pet_type" -> (specs / "redefine_pet_type.yaml"),
        "sttp.tapir.generated.redefine.pet_nullable" -> (specs / "redefine_pet_nullable.yaml"),
        "sttp.tapir.generated.redefine.enum" -> (specs / "redefine_enum.yaml"),
        "sttp.tapir.generated.redefine.nested" -> (specs / "redefine_nested.yaml"),
        "sttp.tapir.generated.redefine.oneof" -> (specs / "redefine_oneof.yaml"),
        "sttp.tapir.generated.redefine.map" -> (specs / "redefine_map.yaml"),
        "sttp.tapir.generated.redefine.array" -> (specs / "redefine_array.yaml"),
        "sttp.tapir.generated.redefine.alias" -> (specs / "redefine_simple_alias.yaml"),
        "sttp.tapir.generated.redefine.default" -> (specs / "redefine_default.yaml"),
        "sttp.tapir.generated.redefine.allof_pet" -> (specs / "redefine_allof_pet.yaml"),
        "sttp.tapir.generated.redefine.shared_widget" -> (specs / "redefine_shared_widget.yaml"),
        // mixed / transitive
        "sttp.tapir.generated.partial.order" -> (specs / "partial_reuse_order.yaml"),
        "sttp.tapir.generated.transitive.mismatch" -> (specs / "transitive_mismatch.yaml"),
        // dependency chain (leaf -> mid -> core)
        "sttp.tapir.generated.chain.mid" -> (specs / "chain_mid.yaml"),
        "sttp.tapir.generated.chain.leaf" -> (specs / "chain_leaf.yaml"),
        // multiple consumers of same core type
        "sttp.tapir.generated.shared.a" -> (specs / "shared_consumer_a.yaml"),
        "sttp.tapir.generated.shared.b" -> (specs / "shared_consumer_b.yaml"),
        // inline body vs named schema
        "sttp.tapir.generated.inline.pet" -> (specs / "inline_pet.yaml")
      )
    },
    openapiPackageDependencies := Map(
      // versioned API
      "sttp.tapir.generated.v1" -> "sttp.tapir.generated.v2",
      // all core dependents
      "sttp.tapir.generated.reuse.all" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.reuse.enum" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.reuse.nested" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.reuse.oneof" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.reuse.map" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.reuse.array" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.reuse.alias" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.reuse.allof_pet" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.transitive.reuse" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.redefine.pet_extra" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.redefine.pet_required" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.redefine.pet_type" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.redefine.pet_nullable" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.redefine.enum" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.redefine.nested" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.redefine.oneof" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.redefine.map" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.redefine.array" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.redefine.alias" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.redefine.default" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.redefine.allof_pet" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.redefine.shared_widget" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.partial.order" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.transitive.mismatch" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.shared.a" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.shared.b" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.inline.pet" -> "sttp.tapir.generated.core",
      // chain
      "sttp.tapir.generated.chain.mid" -> "sttp.tapir.generated.core",
      "sttp.tapir.generated.chain.leaf" -> "sttp.tapir.generated.chain.mid"
    )
  )

libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "1.10.0"
libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % "1.10.0"
libraryDependencies += "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.8.0"
libraryDependencies += "com.chuusai" %% "shapeless" % "2.3.10"
libraryDependencies += "com.beachape" %% "enumeratum" % "1.7.5"
libraryDependencies += "com.beachape" %% "enumeratum-circe" % "1.9.7"

import scala.io.Source
import scala.util.Using

TaskKey[Unit]("check") := {
  def check(generatedFileName: String, expectedFileName: String) = {
    val generatedCode =
      Using(Source.fromFile(s"${sourceManaged.value}/main/sttp/tapir/generated/$generatedFileName"))(_.getLines.mkString("\n")).get
    val expectedCode = Using(Source.fromFile(expectedFileName))(_.getLines.mkString("\n")).get
    val generatedTrimmed =
      generatedCode.linesIterator.zipWithIndex.filterNot(_._1.isBlank).map { case (a, i) => a.trim -> i }.toSeq
    val expectedTrimmed = expectedCode.linesIterator.filterNot(_.isBlank).map(_.trim).toSeq
    generatedTrimmed.zip(expectedTrimmed).foreach { case ((a, i), b) =>
      if (a != b) sys.error(s"Generated code in $generatedFileName did not match (expected '$b' on line $i, found '$a')")
    }
    if (generatedTrimmed.size != expectedTrimmed.size)
      sys.error(s"expected ${expectedTrimmed.size} non-empty lines in $generatedFileName, found ${generatedTrimmed.size}")
  }
  Seq(
    "v2/TapirGeneratedEndpoints.scala" -> "ExpectedV2.scala.txt",
    "v1/TapirGeneratedEndpoints.scala" -> "ExpectedV1.scala.txt",
    "v1/TapirGeneratedEndpointsJsonSerdes.scala" -> "ExpectedV1JsonSerdes.scala.txt"
  ).foreach { case (generated, expected) => check(generated, expected) }
  ()
}
