# Stability of modules

The modules are categorised using the following levels:

* **stable**: binary compatibility is guaranteed within a major version; adheres to semantic versioning
* **stabilising**: the API is mostly stable, with rare binary-incompatible changes possible in minor releases (only if necessary)
* **experimental**: API can change significantly even in patch releases

The major version is increased when there are binary-incompatible changes in **stable** modules.

The minor version is increased when there are significant new features in **stable** modules (keeping compatibility), or binary-incompatible changes in **stabilising** modules.

The patch version is increased when there are binary-compatible changes in **stable** / **stabilising** modules, any changes in **exeperimental** modules, or when a new module is added (e.g. a new integration).

## Main modules 

| Module         | Level       |
|----------------|-------------|
| core (Scala 2) | stable      |
| core (Scala 3) | stabilising |
| server-core    | stabilising |
| client-core    | stabilising |
| files          | stabilising |

## Server interpreters

| Module    | Level        |
|-----------|--------------|
| akka-http | stabilising  |
| armeria   | stabilising  |
| finatra   | stabilising  |
| http4s    | stabilising  |
| netty     | stabilising  |
| nima      | experimental |
| pekko-http| stabilising  |
| play      | stabilising  |
| vertx     | stabilising  |
| zio-http  | stabilising  |

## Client interpreters

| Module | Level       |
|--------|-------------|
| sttp   | stabilising |
| play   | stabilising |
| http4s | stabilising |

## Documentation interpreters

| Module   | Level       |
|----------|-------------|
| openapi  | stabilising |
| asyncapi | stabilising |

## Serverless interpreters

| Module        | Level        |
|---------------|--------------|
| aws-lambda    | experimental |
| aws-sam       | experimental |
| aws-terraform | experimental |

## Integration modules

| Module        | Level        |
|---------------|--------------|
| cats          | stabilising  |
| cats-effect   | stabilising  |
| derevo        | stabilising  |
| enumeratum    | stabilising  |
| newtype       | stabilising  |
| monix-newtype | stabilising  |
| refined       | stabilising  |
| zio           | stabilising  |
| zio-prelude   | experimental |
| iron          | experimental |

## JSON modules

| Module     | Level        |
|------------|--------------|
| circe      | stabilising  |
| json4s     | stabilising  |
| jsoniter   | stabilising  |
| play-json  | stabilising  |
| spray-json | stabilising  |
| tethys     | stabilising  |
| upickle    | stabilising  |
| pickler    | experimental |
| zio-json   | experimental |

## Testing modules

| Module    | Level        |
|-----------|--------------|
| testing   | stabilising  |
| sttp-mock | experimental |
| sttp-stub | stabilising  |

## Observability modules

| Module                | Level       |
|-----------------------|-------------|
| opentelemetry-metrics | stabilising |
| prometheus-metrics    | stabilising |

## Other modules

| Module             | Level        |
|--------------------|--------------|
| openapi-codegen    | experimental |
