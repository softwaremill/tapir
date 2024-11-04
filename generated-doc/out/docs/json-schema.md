# Generating JSON Schema

You can conveniently generate JSON schema from Tapir schema, which can be derived from your Scala types. Use `TapirSchemaToJsonSchema`:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-apispec-docs" % "1.11.8"
```

Schema generation can now be performed like in the following example:

```scala
import sttp.apispec.{Schema => ASchema}
import sttp.tapir.*
import sttp.tapir.docs.apispec.schema.*
import sttp.tapir.generic.auto.*

object Childhood {
  case class Child(age: Int, height: Option[Int])
}
case class Parent(innerChildField: Child, childDetails: Childhood.Child)
case class Child(childName: String) // to illustrate unique name generation
val tSchema = implicitly[Schema[Parent]]

val jsonSchema: ASchema = TapirSchemaToJsonSchema(
  tSchema,
  markOptionsAsNullable = true,
  metaSchema = MetaSchemaDraft04 // default
  // schemaName = sttp.atpir.docs.apispec.defaultSchemaName // default
)
```

All the nested schemas will be referenced from the `$defs` element.

## Serializing JSON Schema
In order to generate a JSON representation of the schema, you can use Circe. For example, with sttp [jsonschema-circe](https://github.com/softwaremill/sttp-apispec) module:

```scala
"com.softwaremill.sttp.apispec" %% "jsonschema-circe" % "..."
```

you will get a codec for `sttp.apispec.Schema`:

```scala
import io.circe.Printer
import io.circe.syntax.*
import sttp.apispec.circe.*
import sttp.apispec.{Schema => ASchema}
import sttp.tapir.*
import sttp.tapir.docs.apispec.schema.*
import sttp.tapir.generic.auto.*
import sttp.tapir.Schema.annotations.title

object Childhood {
  @title("my child") case class Child(age: Int, height: Option[Int])
}
case class Parent(innerChildField: Child, childDetails: Childhood.Child)
case class Child(childName: String)
val tSchema = implicitly[Schema[Parent]]

val jsonSchema: ASchema = TapirSchemaToJsonSchema(
  tSchema,
  markOptionsAsNullable = true)

// JSON serialization
val schemaAsJson = jsonSchema.asJson
val schemaStr: String = Printer.spaces2.print(schemaAsJson.deepDropNullValues)
```

The title annotation of the object will be by default the name of the case class. You can customize it with `@title` annotation.
You can also disable generation of default title fields by setting an option `addTitleToDefs` to `false`.  This example will produce following String:

```json
{
  "$schema" : "http://json-schema.org/draft-04/schema#",
  "required" : [
    "innerChildField",
    "childDetails"
  ],
  "type" : "object",
  "properties" : {
    "innerChildField" : {
      "$ref" : "#/$defs/Child"
    },
    "childDetails" : {
      "$ref" : "#/$defs/Child1"
    }
  },
  "$defs" : {
    "Child" : {
      "title" : "Child",
      "required" : [
        "childName"
      ],
      "type" : "object",
      "properties" : {
        "childName" : {
          "type" : "string"
        }
      }
    },
    "Child1" : {
      "title" : "my child",
      "required" : [
        "age"
      ],
      "type" : "object",
      "properties" : {
        "age" : {
          "type" : "integer",
          "format" : "int32"
        },
        "height" : {
          "type" : [
            "integer",
            "null"
          ],
          "format" : "int32"
        }
      }
    }
  }
}
```
