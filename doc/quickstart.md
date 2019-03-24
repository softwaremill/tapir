# Quickstart

To use tapir, add the following dependency to your project:

```scala
"com.softwaremill.tapir" %% "tapir-core" % "0.4"
```

This will import only the core classes. To generate a server or a client, you will need to add further dependencies.

Most of tapir functionalities use package objects which provide builder and extensions methods, hence it's easiest to 
work with tapir if you import whole packages, e.g.:

```scala
import tapir._
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

## Example usages

To see an example project using Tapir, [check out this Todo-Backend](https://github.com/hejfelix/tapir-http4s-todo-mvc) 
using tapir and http4s.

Also check out the simple [runnable example](https://github.com/softwaremill/tapir/blob/master/playground/src/main/scala/tapir/example/BooksExample.scala)
which is available in the repository.

## StackOverflowException during compilation

Sidenote for scala 2.12.4 and higher: if you encounter an issue with compiling your project because of 
a `StackOverflowException` related to [this](https://github.com/scala/bug/issues/10604) scala bug, 
please increase your stack memory. Example:

```
sbt -J-Xss4M clean compile
```
