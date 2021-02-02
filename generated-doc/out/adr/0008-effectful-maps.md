# 7. No effectful maps

Date: 2021-01-26

## Context

Currently any mapping of endpoint inputs and outputs can't have side effects, that is we only allow simple functions
`T => U` & `U => T` to define the mapping.

It would be possible to add effectful mappings by extending the endpoint input/output with a capabilities type 
parameter. Any endpoints mapped with an effect would require the `Effect[F]` capability, which would have to
be supported by the interpreter.

## Decision

This could be implemented as a family of functions, in the most general form:

```scala
trait EndpointInput[T, -R] {
  // ...
  def mapF[F[_], U](f: T => F[U])(g: U => F[T]): EndpointInput[U, R with Effect[F]] = ???
}
```

However, the utility of such effectful maps seems limited. One of the possible use-cases would be to map an id-input
to the entity, looked up from the database. For example, if we've got users who are identified using `Long` ids, we
would like to convert an `EndpointInput[Long]` into a `EndpointInput[User]`. For that, we would need a function:
`Long => F[User]` and a trivial one `User => F[Long]`, which would just return the id wrapped in the effect. However:

1. Typically, we don't have a function `Long => F[User]`, but a `Long => F[Either[Error, User]]`. We could represent
errors as failed effects and use failure handlers to return responses, but that's a non-type-safe and a global solution.
Note that when using e.g. `.serverLogicForCurrent` this is already taken care of, as the result can be an error, which
is mapped to the endpoint's errors.
2. The mapping seems only to be useful on the server side. When using an endpoint as a client, we probably don't have
the `User` instance, but only the id. Hence, we wouldn't be able to provide the value necessary for the client call 
anyway.

Summing up, it seems that the current solution with two approaches to partially defining the server logic seem 
sufficient and better. As no other compelling use-cases have been identified, we're keeping the current design as-is, 
without introducing effectful mappings.