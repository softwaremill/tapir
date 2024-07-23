# Scala 2, Scala 3; JVM, JS & Native

Tapir is available for Scala 3.3+, Scala 2.13 and Scala 2.12, on the JVM, JS and Native platforms. 

Note that not all  modules are available for all combinations of the above. This specifically applies to Scala.JS and 
Scala Native, where support is limited. The JVM modules require Java 11+, with a couple of exceptions, which require 
Java 21+ - this is marked in the documentation.

## In the documentation & examples

The documentation & examples are written & compiled using Scala 3. To compile example code with Scala 2, some 
adjustments might be necessary:

* For wildcard imports, use `_` instead of `*`, e.g. instead of `import sttp.tapir.*`, use `import sttp.tapir._`
* For the main method, instead of `@main`, use an `object MyApp extends App`, e.g.:

```scala
// in Scala 3:
@main def myExample(): Unit = /* body */

// in Scala 2:
object MyExample extends App {
  /* body */
}
```

* Instead of `given` definitions, use `implicit val` or `implicit def` (for codecs, schemas etc.). E.g.:

```scala
// in Scala 3:
given Schema[MyType] = Schema.derived

// in Scala 2:
implicit val myTypeSchema: Schema[MyType] = Schema.derived
```

* Use curly braces around class & method definitions. E.g.:

```scala
// in Scala 3:
class MyClass:
  def myMethod(): Unit =
    val z = 2
    z + 2

// in Scala 2:
class MyClass {
  def myMethod(): Unit = {
    val z = 2
    z + 2
  }
}
```

## Scala 2.12

Partial unification is now enabled by default from Scala 2.13. However, if you're using Scala 2.12 or older, and don't
have it already, you'll want to to enable partial unification in the compiler (alternatively, you'll need to manually
provide type arguments in some cases). In sbt, this is:

```scala
scalacOptions += "-Ypartial-unification"
```
