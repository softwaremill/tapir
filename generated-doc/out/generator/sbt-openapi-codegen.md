# Generate endpoint definitions from an OpenAPI YAML

```{note}
This is a relatively mature implementation that should be sufficiently capable for the majority of use-cases, but
nonetheless does not yet completely cover the openapi spec. Pull requests or issues for missing or
incorrectly-implemented functionality are highly encouraged.
```

## Installation steps

Add the sbt plugin to the `project/plugins.sbt`:

```scala
addSbtPlugin("com.softwaremill.sttp.tapir" % "sbt-openapi-codegen" % "1.12.4")
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
openapiXmlSerdeLib                    cats-xml                             The xml serde library to use.
openapiValidateNonDiscriminatedOneOfs true                                 Whether to fail if variants of a oneOf without a discriminator cannot be disambiguated.
openapiMaxSchemasPerFile              400                                  Maximum number of schemas to generate in a single file (tweak if hitting javac class size limits).
openapiAdditionalPackages             Nil                                  Additional packageName/swaggerFile pairs for generating from multiple schemas 
openapiStreamingImplementation        fs2                                  Implementation for streamTextBody. Supports: akka, fs2, pekko, zio.
                                                                           fs2 defaults to using the IO effect -- an alternative effect type can be specified with fs2-my.fully.qualified.Effect
openapiGenerateEndpointTypes          false                                Whether to emit explicit types for endpoint defns
openapiDisableValidatorGeneration     false                                If true, we will not generate validation for constraints (min, max, pattern etc)
openapiUseCustomJsoniterSerdes        false                                If true and openapiJsonSerdeLib = jsoniter, serdes will be generated to use custom 'openapi' make defns. May help with flaky compilation, but requires jsoniter-scala >= 2.36.0+
===================================== ==================================== ==================================================================================================
```

The general usage is;

```scala
import sttp.apispec.openapi.circe.yaml.*
import sttp.tapir.generated.*
import sttp.tapir.docs.openapi.*

val docs = TapirGeneratedEndpoints.generatedEndpoints.toOpenAPI("My Bookshop", "1.0")
```

### Support specification extensions

Generator behaviour can be further configured by specifications on the input openapi spec. Example:

```yaml
paths:
  x-tapir-codegen-security-path-prefixes:
    - '/security-group/{securityGroupName}' # any path prefixes matching this pattern will be considered 'securityIn' 
  '/my-endpoint':
    post:
      x-tapir-codegen-directives: [ 'json-body-as-string' ] # This will customise what the codegen generates for this endpoint
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/MyModel'
      responses:
        "204":
          description: "No response"
```

Supported specifications are:

- x-tapir-codegen-security-path-prefixes: supported on the paths object. This is an array of strings representing path
  prefixes. The longest matching prefix of each path will be treated as a security input, rather than as a standard 'in'
  value.
- x-tapir-codegen-directives: supported on openapi operations. This is an array of string flags. Supported values are:

```{eval-rst}
========================= ===================================================================================================================================
name                      description
========================= ===================================================================================================================================
json-body-as-string       If present on an operation, all application/json requests and responses will be interpreted mapped to a string with stringJsonBody
force-eager               If present on an operation, all content types will be forced to eager, even if the default implementation is streaming
force-streaming           If present on an operation, all content types will be forced to streaming, even if the default implementation is eager, unless it is in error position (which is always eager)
force-req-body-eager      Like force-eager, but applies only to req body
force-resp-body-eager     Like force-eager, but applies only to resp body
force-req-body-streaming  Like force-streaming, but applies only to req body
force-resp-body-streaming Like force-streaming, but applies only to resp body
========================= ===================================================================================================================================
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
jsoniter              "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"   "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-circe" (free-form json support)
                      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros"
===================== ================================================================== ===================================================================
```

### XML Support

Xml support is still fairly experimental. Available options are 'cats-xml' and 'none'. 'none' will fallback to a
streaming binary 'non-implementation'. The minimal supported version of cats-xml is 0.0.20 for scala 2 and TBD for
scala 3.

```{eval-rst}
===================== ========================================================================================
 openapiXmlSerdeLib          required dependencies
===================== ========================================================================================
cats-xml              "com.github.geirolz" %% "cats-xml"
                      "com.github.geirolz" %% "cats-xml-generic"
none
===================== ========================================================================================
```

### Limitations

Currently, string-like enums in Scala 2 depend upon the enumeratum library (`"com.beachape" %% "enumeratum"`).
For Scala 3 we derive native enums, and depend on `"io.github.bishabosha" %% "enum-extensions"` for generating query
param serdes.

Models containing binary data cannot be re-used between json and multi-part form endpoints, due to having different
representation types for the binary data

We currently miss a few OpenApi features. Notable are:

- anyOf
- not all validation is supported (readOnly/writeOnly, and minProperties/maxProperties on heterogeneous object schemas,
  are currently unsupported)
- missing model types (date, duration, etc)

