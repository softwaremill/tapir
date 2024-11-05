// {cat=JSON; effects=Direct; server=Netty; JSON=jsoniter}: Return a JSON response with Jsoniter

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-jsoniter-scala:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.8
//> using dep ch.qos.logback:logback-classic:1.5.8
//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:2.30.15

package sttp.tapir.examples.json

import sttp.tapir.*
import sttp.tapir.server.netty.sync.NettySyncServer
import sttp.tapir.json.jsoniter.*
import sttp.tapir.generic.auto.*
import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*

@main def jsoniterNettySyncServer(): Unit =
  case class Country(name: String)
  case class Author(name: String, country: Country)
  case class Genre(name: String)
  case class Book(title: String, genre: Genre, year: Int, author: Author)

  // we need to derive jsoniter's codec for the top-level class
  given bookCodec: JsonValueCodec[Book] = JsonCodecMaker.make

  val helloWorld = endpoint.get
    .in("hello")
    // uses the derived JsonValueCodec & an auto-derived Tapir's Schema, through the `auto` import
    .out(jsonBody[Book])
    .handleSuccess(_ => Book("The Witcher: The Last Wish", Genre("Fantasy"), 1993, Author("Andrzej Sapkowski", Country("Poland"))))

  NettySyncServer().addEndpoint(helloWorld).startAndWait()
