# 9. Lists are optional

Date: 2021-09-08

## Context

When deriving or implicitly inferring a schema for a collection-value type `T` (e.g. `List[String]`), should the 
resulting `Schema[T]` be optional, or not? That is, what should be the value of the `Schema[T].isOptional` field?

On one hand, the collection can be empty, just as an `Option[T]` can be empty, which would indicate that this value
is optional. On the other, when a json object contains a collection-valued field, there are four possible states:
an entirely missing field, a field with a `null` value, an empty array `[]`, or a non-empty array. In this scenario,
one could consider `List[String]` to allow only empty and non-empty array values, while `Option[List[String]]` would
allow all four.

## Decision

Schemas for collection-valued types are optional. This is because when deriving/inferring schemas, there is no context,
that is, we don't know whether we are deriving a schema for a query parameter, a header, or a json field. In many cases,
making collection schemas optional is the only sensible choice: e.g. `query[List[String]]("x")` is obviously optional, 
as there are only two possible states: either there are no query parameters with name `x`, or there are multiple ones.
Similar for headers. Even for body values, this often makes the same sense: when parsing the body as a comma-separated
list of values, or when generating the schema for an xml body, where children elements can be repeated or not (there
are no `null` values).

This is different for json, though, as a field can be absent or `null`-valued. In the current representation, `Schema`s
make no distinction between a missing field, or an empty array. Json parsers might behave differently - though this 
isn't something that can be generically supported; so either the json parsers, or the schemas will have to be adjusted
accordingly.

As a work-around, if needed, there are several options:

* use a dedicated type, for which a schema can be separately defined, just as the schema for `NonEmptyList` from the   
  cats integration is required
* define a local implicit:

```scala
implicit def schemaForIterable[T: Schema, C[X] <: Iterable[X]]: Schema[C[T]] = 
  implicitly[Schema[T]].asIterable[C].copy(isOptional = false)
```

when deriving the schema for a type that is later serialised to json, e.g. in a block in which `jsonBody` is called.

* [customise](https://tapir.softwaremill.com/en/latest/endpoint/schemas.html#customising-derived-schemas) the derived 
  schemas