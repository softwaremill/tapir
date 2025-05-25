// {cat=Custom types; effects=Direct; server=Netty}: A query parameter which maps to a Scala 3 enum (enumeration)

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.32
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.32
//> using dep ch.qos.logback:logback-classic:1.5.8

package sttp.tapir.examples.custom_types

import sttp.tapir.*
import sttp.tapir.server.netty.sync.NettySyncServer

enum PieType(val index: Int, val name: String):
  case Apple extends PieType(1, "apple")
  case Orange extends PieType(2, "orange")
  case BananaCustard extends PieType(3, "banana-custard")

@main def enumQueryParameter(): Unit =
  given Codec[String, PieType, CodecFormat.TextPlain] =
    Codec.derivedEnumeration[String, PieType](decode = s => PieType.values.find(_.name == s), encode = _.name)

  val bake = endpoint.get
    .in("bake")
    .in(query[PieType]("pie"))
    .out(stringBody)
    .handleSuccess(pie => s"Baking: $pie!")

  NettySyncServer().addEndpoint(bake).startAndWait()
