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

## JDK version

To ensure that Tapir can be used in a wide range of projects, the CI job uses JDK11 for most of the modules. There are exceptions (like `netty-server-loom` and `nima-server`) which require JDK version >= 21. This requirement is adressed by the build matrix in `.github/workflows/ci.yml`, which runs separate builds on a newer Java version, and sets a `ONLY_LOOM` env variable, used by build.sbt to recognise that it should limit the scope of an aggregated task to these projects only.
For local development, feel free to use any JDK >= 11. You can be on JDK 21, then with missing `ONLY_LOOM` variable you can still run sbt tasks on projects excluded from aggegate build, for example:
```scala
nimaServer/Test/test
nettyServerLoom/compile
// etc.
```

## Acknowledgments

Tuple-concatenating code is copied from [akka-http](https://github.com/akka/akka-http/blob/master/akka-http/src/main/scala/akka/http/scaladsl/server/util/TupleOps.scala)

Parts of generic derivation configuration is copied from [circe](https://github.com/circe/circe/blob/master/modules/generic-extras/src/main/scala/io/circe/generic/extras/Configuration.scala)

Implementation of mirror for union and intersection types are originally implemented by [Iltotore](https://github.com/Iltotore) in [this gist](https://gist.github.com/Iltotore/eece20188d383f7aee16a0b89eeb887f)

Tapir logo & stickers have been drawn by [impurepics](https://twitter.com/impurepics).
