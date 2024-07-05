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
