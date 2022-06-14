# Stability of modules

The modules are categorised using the following levels:

* **stable**: binary compatibility is guaranteed within a major version; adheres to semantic versioning
* **stabilising**: the API is mostly stable, with rare binary-incompatible changes possible in minor releases (only if necessary)
* **experimental**: API can change significantly even in patch releases

## Main modules 

| Module         | Level       |
|----------------|-------------|
| core (Scala 2) | stable      |
| core (Scala 3) | stabilising |
| server-core    | stabilising |
| client-core    | stabilising |

## Server interpreters

| Module    | Level        |
|-----------|--------------|
| akka-http | stabilising  |
| armeria   | stabilising  |
| finatra   | stabilising  |
| http4s    | stabilising  |
| netty     | experimental |
| play      | stabilising  |
| vertx     | stabilising  |
| zio1-http | experimental |
| zio-http  | experimental |

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

| Module     | Level        |
|------------|--------------|
| cats       | stabilising  |
| derevo     | stabilising  |
| enumeratum | stabilising  |
| newtype    | stabilising  |
| refined    | stabilising  |
| zio        | experimental |
| zio1       | stabilising  |

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
| zio-json   | experimental |
| zio1-json  | experimental |

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
