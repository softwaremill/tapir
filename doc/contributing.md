# Contributing

All suggestions welcome :)!

If you'd like to contribute, see the list of [issues](https://github.com/softwaremill/tapir/issues) and pick one!
Or report your own. If you have an idea you'd like to discuss, that's always a good option.

If you are having doubts on the _why_ or _how_ something works, don't hesitate to ask a question on
[discourse](https://softwaremill.community/c/tapir) or via github. This probably means that the documentation, scaladocs or
code is unclear and can be improved for the benefit of all.

## Conventions

### Enumerations

Scala 3 introduces `enum`, which can be used to represent sealed hierarchies with simpler syntax, or actual "true" enumerations,
that is parameterless enums or sealed traits with only case objects as children. Tapir needs to treat the latter differently,
in order to allow using OpenAPI `enum` elements and derive JSON codecs which represent them as simple values (without discriminator).
Let's use the name `enumeration` in Tapir codebase to represent these "true" enumerations and avoid ambiguity.

## Acknowledgments

Tuple-concatenating code is copied from [akka-http](https://github.com/akka/akka-http/blob/master/akka-http/src/main/scala/akka/http/scaladsl/server/util/TupleOps.scala)

Parts of generic derivation configuration is copied from [circe](https://github.com/circe/circe/blob/master/modules/generic-extras/src/main/scala/io/circe/generic/extras/Configuration.scala)

Implementation of mirror for union and intersection types are originally implemented by [Iltotore](https://github.com/Iltotore) in [this gist](https://gist.github.com/Iltotore/eece20188d383f7aee16a0b89eeb887f)

Tapir logo & stickers have been drawn by [impurepics](https://twitter.com/impurepics).
