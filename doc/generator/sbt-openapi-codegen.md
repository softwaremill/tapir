# Generate endpoint definitions from an OpenAPI YAML

```eval_rst
.. note::

  This is a really early alpha implementation.
```

## Installation steps

Add the sbt plugin to the `project/plugins.sbt`:

```scala
addSbtPlugin("com.softwaremill.sttp.tapir" % "sbt-openapi-codegen" % "@VERSION@")
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

```eval_rst
=============================== ==================================== =====================================================================
setting                         default value                        description
=============================== ==================================== =====================================================================
openapiSwaggerFile              baseDirectory.value / "swagger.yaml" The swagger file with the api definitions.
openapiPackage                  sttp.tapir.generated                 The name for the generated package.
openapiObject                   TapirGeneratedEndpoints              The name for the generated object.
openapiUseHeadTagForObjectName  false                                If true, put endpoints in separate files based on first declared tag.
=============================== ==================================== =====================================================================
```

The general usage is;

```scala
import sttp.apispec.openapi.circe.yaml._
import sttp.tapir.generated._
import sttp.tapir.docs.openapi._

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

### Limitations

Currently, the generated code depends on `"io.circe" %% "circe-generic"`. In the future probably we will make the
encoder/decoder json lib configurable (PRs welcome).

String-like enums in Scala 2 depend on both `"com.beachape" %% "enumeratum"` and `"com.beachape" %% "enumeratum-circe"`.
For Scala 3 we derive native enums, and depend on `"org.latestbit" %% "circe-tagged-adt-codec"` for json serdes
and `"io.github.bishabosha" %% "enum-extensions"` for query param serdes.
Other forms of OpenApi enum are not currently supported.

Models containing binary data cannot be re-used between json and multi-part form endpoints, due to having different
representation types for the binary data

We currently miss a lot of OpenApi features like:

- ADTs
- missing model types and meta descriptions (like date, minLength)

