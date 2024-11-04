# Generate endpoint definitions from an OpenAPI YAML

```{note}
This is a really early alpha implementation.
```

## Installation steps

Add the sbt plugin to the `project/plugins.sbt`:

```scala
addSbtPlugin("com.softwaremill.sttp.tapir" % "sbt-openapi-codegen" % "1.11.8")
```

Enable the plugin for your project in the `build.sbt`:

```scala
enablePlugins(OpenapiCodegenPlugin)
```

Add your OpenApi file to the project, and override the `openapiSwaggerFile` setting in the `build.sbt`:

```scala
openapiSwaggerFile := baseDirectory.value / "swagger.yaml"
```

At this point your compile step will try to generate the endpoint definitions
to the `sttp.tapir.generated.TapirGeneratedEndpoints` object, where you can access the
defined case-classes and endpoint definitions.

## Usage and options

The generator currently supports these settings, you can override them in the `build.sbt`;

```{eval-rst}
===================================== ==================================== ==================================================================================================
setting                               default value                        description
===================================== ==================================== ==================================================================================================
openapiSwaggerFile                    baseDirectory.value / "swagger.yaml" The swagger file with the api definitions.
openapiPackage                        sttp.tapir.generated                 The name for the generated package.
openapiObject                         TapirGeneratedEndpoints              The name for the generated object.
openapiUseHeadTagForObjectName        false                                If true, put endpoints in separate files based on first declared tag.
openapiJsonSerdeLib                   circe                                The json serde library to use.
openapiValidateNonDiscriminatedOneOfs true                                 Whether to fail if variants of a oneOf without a discriminator cannot be disambiguated.
openapiMaxSchemasPerFile              400                                  Maximum number of schemas to generate in a single file (tweak if hitting javac class size limits).
openapiAdditionalPackages             Nil                                  Additional packageName/swaggerFile pairs for generating from multiple schemas 
===================================== ==================================== ==================================================================================================
```

The general usage is;

```scala
import sttp.apispec.openapi.circe.yaml.*
import sttp.tapir.generated.*
import sttp.tapir.docs.openapi.*

val docs = TapirGeneratedEndpoints.generatedEndpoints.toOpenAPI("My Bookshop", "1.0")
```

### Output files

To expand on the `openapiUseHeadTagForObjectName` setting a little more, suppose we have the following endpoints:

```yaml
paths:
  /foo:
    get:
      tags:
        - Baz
        - Foo
    put:
      tags: [ ]
  /bar:
    get:
      tags:
        - Baz
        - Bar
```

In this case 'head' tag for `GET /foo` and `GET /bar` would be 'Baz', and `PUT /foo` has no tags (and thus no 'head'
tag).

If `openapiUseHeadTagForObjectName = false` (assuming default settings for the other flags) then all endpoint
definitions
will be output to the `TapirGeneratedEndpoints.scala` file, which will contain a
single `object TapirGeneratedEndpoints`.

If `openapiUseHeadTagForObjectName = true`, then the  `GET /foo` and `GET /bar` endpoints would be output to a
`Baz.scala` file, containing a single `object Baz` with those endpoint definitions; the `PUT /foo` endpoint, by dint of
having no tags, would be output to the `TapirGeneratedEndpoints` file, along with any schema and parameter definitions.

Files can be generated from multiple openapi schemas if `openapiAdditionalPackages` is configured; for example:

```scala
openapiAdditionalPackages := List(
      "sttp.tapir.generated.v1" -> baseDirectory.value / "src" / "main" / "resources" / "openapi_v1.yml")
```
would generate files in the package `sttp.tapir.generated.v1` based on the `openapi_v1.yml` schema at the provided
location. This would be in addition to files generated in `openapiPackage` from the specs configured by
`openapiSwaggerFile`


### Json Support

```{eval-rst}
===================== ================================================================== ===================================================================
 openapiJsonSerdeLib         required dependencies                                       Conditional requirements
===================== ================================================================== ===================================================================
circe                 "io.circe" %% "circe-core"                                         "com.beachape" %% "enumeratum-circe" (scala 2 enum support).
                      "io.circe" %% "circe-generic"                                      "org.latestbit" %% "circe-tagged-adt-codec" (scala 3 enum support).
jsoniter              "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"
                      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros"
===================== ================================================================== ===================================================================
```

### Limitations

Currently, string-like enums in Scala 2 depend upon the enumeratum library (`"com.beachape" %% "enumeratum"`).
For Scala 3 we derive native enums, and depend on `"io.github.bishabosha" %% "enum-extensions"` for generating query
param serdes.

Models containing binary data cannot be re-used between json and multi-part form endpoints, due to having different
representation types for the binary data

We currently miss a lot of OpenApi features like:

- tags
- anyOf/allOf
- missing model types and meta descriptions (like date, minLength)
- file handling

