// {cat=JSON; effects=Direct; server=Netty; JSON=circe}: Return a JSON response with Circe and auto-dervied codecs

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.8
//> using dep ch.qos.logback:logback-classic:1.5.8

package sttp.tapir.examples.json

import sttp.tapir.*
import sttp.tapir.server.netty.sync.NettySyncServer
import sttp.tapir.json.circe.*
import sttp.tapir.generic.auto.*
import io.circe.generic.auto.*

@main def circeNettySyncServer(): Unit =
  case class Country(name: String)
  case class Author(name: String, country: Country)
  case class Genre(name: String)
  case class Book(title: String, genre: Genre, year: Int, author: Author)

  val helloWorld = endpoint.get
    .in("hello")
    // both Tapir's Schema & circe's Encoder/Decoder are automatically derived thanks to the `auto` imports
    .out(jsonBody[Book])
    .handleSuccess(_ => Book("The Witcher: The Last Wish", Genre("Fantasy"), 1993, Author("Andrzej Sapkowski", Country("Poland"))))

  NettySyncServer().addEndpoint(helloWorld).startAndWait()
