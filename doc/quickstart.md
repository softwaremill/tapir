# Quickstart

To use tapir, add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-core" % "0.13.1"
```

This will import only the core classes needed to create endpoint descriptions. To generate a server or a client, you
will need to add further dependencies.

Most of tapir functionalities are grouped into package objects which provide builder and extensions methods, hence it's
easiest to work with tapir if you import whole packages, e.g.:

```scala
import sttp.tapir._
```

If you don't have it already, you'll also need partial unification enabled in the compiler (alternatively, you'll need 
to manually provide type arguments in some cases). In sbt, this is:

```scala
scalacOptions += "-Ypartial-unification"
```

Finally, type:

```scala
endpoint.
```

and see where auto-complete gets you!
