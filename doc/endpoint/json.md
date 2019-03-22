# Working with JSON

Json values are supported through codecs which encode/decode values to json strings. However, third-party libraries are
needed for actual json parsing/printing. Currently, [Circe](https://github.com/circe/circe) is supported. To use, add
the following dependency to your project:

```scala
"com.softwaremill.tapir" %% "tapir-json-circe" % "0.4"
```

Next, import the package (or extend the `JsonCirce` trait, see [MyTapir](../mytapir.html)):

```scala
import tapir.json.circe._
```

This will bring into scope `Codec`s which, given an in-scope circe `Encoder`/`Decoder`, will create a codec using the 
json media type. Circe includes a couple of approaches to generating encoders/decoders (manual, semi-auto and auto), so you may choose
whatever suits you. 

For example, to automatically generate a JSON codec for a case class:

```scala
import tapir._
import tapir.json.circe._
import io.circe.generic.auto._

case class Book(author: String, title: String, year: Int)

val bookInput: EndpointIO[Book] = jsonBody[Book]
```

To add support for other JSON libraries, see the 
[sources](https://github.com/softwaremill/tapir/blob/master/json/circe/src/main/scala/tapir/json/circe/JsonCirce.scala) 
for the Circe codec (which is just a couple of lines of code).

## Next

Read on about [working with forms](forms.html).