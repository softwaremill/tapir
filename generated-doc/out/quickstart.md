# Quickstart

To use tapir, add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-core" % "1.10.11"
```

This will import only the core classes needed to create endpoint descriptions. To generate a server or a client, you
will need to add further dependencies.

Many of tapir functionalities come as builder methods in the main package, hence it's easiest to work with tapir if 
you import the main package entirely, i.e.:

```scala
import sttp.tapir._
```

Finally, type:

```scala
endpoint.
```

and see where auto-complete gets you!

## Scala 2.12

Partial unification is now enabled by default from Scala 2.13. However, if you're using Scala 2.12 or older, and don't
have it already, you'll want to to enable partial unification in the compiler (alternatively, you'll need to manually
provide type arguments in some cases). In sbt, this is:

```scala
scalacOptions += "-Ypartial-unification"
```