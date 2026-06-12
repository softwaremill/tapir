package sttp.tapir.server.netty

import ox.Chunk
import ox.flow.Flow
import sttp.model.sse.ServerSentEvent
import sttp.monad.{IdentityMonad, MonadError}
import sttp.shared.Identity
import sttp.tapir.*

import java.nio.charset.StandardCharsets

package object sync:
  type IdRoute = Route[Identity]
  private[sync] implicit val idMonad: MonadError[Identity] = IdentityMonad

  val serverSentEventsBody: StreamBodyIO[Flow[Chunk[Byte]], Flow[ServerSentEvent], OxStreams] =
    streamTextBody(OxStreams)(CodecFormat.TextEventStream(), Some(StandardCharsets.UTF_8))
      .map(OxServerSentEvents.parseBytesToSSE)(OxServerSentEvents.serializeSSEToBytes)
