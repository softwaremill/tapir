# Handling Delimited Path Parameters

Tapir allows you to handle complex path parameters, such as lists of custom types separated by delimiters (e.g., commas).
This can be achieved using `Codec.delimited`, which facilitates the serialization and deserialization of delimited lists
within path segments.

## Use Case

Suppose you want to define an endpoint that accepts a list of names as a comma-separated path parameter. Each name should
adhere to a specific pattern (e.g., only uppercase letters).

## Implementation Steps:

### 1. Define the Custom Type and Validator
Start by defining your custom type and the associated validator to enforce the desired pattern.

```scala
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.Codec
import sttp.tapir.Validator
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.model.Delimited

case class Name(value: String)

// Validator to ensure names consist of uppercase letters only
val nameValidator: Validator[String] = Validator.pattern("^[A-Z]+$")
```

### 2. Create Codecs for the Custom Type and Delimited List
Utilize `Codec.parsedString` for individual `Name` instances and `Codec.delimited` for handling the list.

```scala
// Codec for single Name
given Codec[String, Name, TextPlain] = Codec.parsedString(Name.apply)
  .validate(nameValidator.contramap(_.value))

// Codec for a list of Names, delimited by commas
given Codec[String, Delimited[",", Name], TextPlain] = Codec.delimited
```

### 3. Define the Endpoint with Delimited Path Parameter
Incorporate the delimited codec into your endpoint definition to handle the list of names in the path.

```scala
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.Codec
import sttp.tapir.Validator
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.model.Delimited

case class Name(value: String)

// Validator to ensure names consist of uppercase letters only
val nameValidator: Validator[String] = Validator.pattern("^[A-Z]+$")

// Codec for single Name
given Codec[String, Name, TextPlain] = Codec.parsedString(Name.apply)
        .validate(nameValidator.contramap(_.value))

// Codec for a list of Names, delimited by commas
given Codec[String, Delimited[",", Name], TextPlain] = Codec.delimited

val getUserEndpoint =
  endpoint.get
    .in("user" / path[Delimited[",", Name]]("id"))
    .out(stringBody)
```

### 4. Generated OpenAPI Schema
When you generate the OpenAPI documentation for this endpoint, the schema for the `id` path parameter will
correctly reflect it as an array with the specified pattern for each item.

```yaml
paths:
  /user/{id}:
    get:
      operationId: getUserId
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: array
            items:
              type: string
              pattern: ^[A-Z]+$
```

## Explanation
- `Codec.parsedString`: Transforms a `String` into a custom type (`Name`) and vice versa. It also applies validation to
  ensure each `Name` adheres to the specified pattern.
- `Codec.delimited`: Handles the serialization and deserialization of a delimited list (e.g., comma-separated) of the
  custom type. By specifying `Delimited[",", Name]`, Tapir knows how to split and join the list based on the delimiter.
- Endpoint Definition: The `path[List[Name]]("id")` indicates that the id path parameter should be treated as a list of
  `Name` objects, utilizing the previously defined codecs.

## Validation
Validators play a crucial role in ensuring that each element within the delimited list meets the required criteria. In
this example, `nameValidator` ensures that each `Name` consists solely of uppercase letters. Tapir applies this validation
to each element in the list, providing robust input validation.