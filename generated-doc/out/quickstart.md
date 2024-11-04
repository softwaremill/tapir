# Quickstart

To use tapir, add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-core" % "1.11.8"
```

This will import only the core classes needed to create endpoint descriptions. To generate a server or a client, you
will need to add further dependencies.

Many of tapir functionalities come as builder methods in the main package, hence it's easiest to work with tapir if 
you import the main package entirely, i.e.:

```scala
import sttp.tapir.*
```

Finally, type:

```scala
endpoint.
```

and see where auto-complete gets you!

