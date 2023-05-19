# Generating JSON Schema

Since Tapir v1.4.0 you can conveniently generate JSON schema from Tapir schema, which can be derived from your Scala types. Use `TapirSchemaToJsonSchema`:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-apispecs-docs" % "@VERSION@"
// if you need to serialize the schema. See https://github.com/softwaremill/sttp-apispec
"com.softwaremill.sttp.apispec" %% "jsonschema-circe" % "..."
```

Schema generation can now be performed like in the following example:

```scala:mdoc:compile-only
import sttp.apispec.circe._
import sttp.apispec.{ReferenceOr, Schema => ASchema, SchemaType => ASchemaType}
import sttp.tapir._
import sttp.tapir.docs.apispec.schema._
import sttp.tapir.generic.auto._

  object Childhood {
    case class Child(age: Int, height: Option[Int])
  }
  case class Parent(innerChildField: Child, childDetails: Childhood.Child)
  case class Child(childName: String) // to illustrate unique name generation
  val tSchema = implicitly[Schema[Parent]]

  val jsonSchema: ReferenceOr[ASchema] = TapirSchemaToJsonSchema(
    tSchema,
    markOptionsAsNullable = true,
    metaSchema = MetaSchemaDraft04, // default
    schemaName = defaultSchemaName // default
)
```

All the nested schemas will be referenced from the `$defs` element.
In order to generate a JSON representation of the schema, you can use Circe:

```scala:mdoc:compile-only
import io.circe.Printer
import io.circe.syntax._
import sttp.apispec.circe._

val schemaAsJson = jsonSchema.getOrElse(ASchemaType.Null).asJson
val schemaStr: String = Printer.spaces2.print(schemaAsJson.deepDropNullValues)
```

This example will produce following String:

```scala:mdoc
{
  "$schema" : "https://json-schema.org/draft-04/schema#",
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
