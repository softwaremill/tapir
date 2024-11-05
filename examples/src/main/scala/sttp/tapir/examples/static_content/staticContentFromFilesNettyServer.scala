// {cat=Static content; effects=Direct; server=Netty}: Serving static files from a directory

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-files:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.8

package sttp.tapir.examples.static_content

import sttp.shared.Identity
import sttp.tapir.emptyInput
import sttp.tapir.files.*
import sttp.tapir.server.netty.sync.NettySyncServer

@main def staticContentFromFilesNettyServer(): Unit =
  NettySyncServer()
    .port(8080)
    .addEndpoints(staticFilesServerEndpoints[Identity](emptyInput)("/var/www"))
    .startAndWait()
