# Quickstart

To use tapir, add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-core" % "0.12.25"
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

## StackOverflowException during compilation

Sidenote for scala 2.12.4 and higher: if you encounter an issue with compiling your project because of 
a `StackOverflowException` related to [this](https://github.com/scala/bug/issues/10604) scala bug, 
please increase your stack memory. Example:

```
sbt -J-Xss4M clean compile
```

## Logging of generated macros code 
For some cases, it may be helpful to examine how generated macros code looks like.
To do that, just set an environmental variable and check compilation logs for details.    

```
export TAPIR_LOG_GENERATED_CODE=true
```
