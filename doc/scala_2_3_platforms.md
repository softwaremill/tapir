# Scala 2, Scala 3 & platforms

Tapir is available for Scala 3.3+, Scala 2.13 and Scala 2.12, on the JVM, JS and Native platforms. 

Note that some modules are unavailable for specific combinations of the above, specifically for Scala.JS and Scala 
Native. The JVM modules require Java 11+, with a couple of exceptions, which require Java 21+ - this is marked in
the documentation.

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
