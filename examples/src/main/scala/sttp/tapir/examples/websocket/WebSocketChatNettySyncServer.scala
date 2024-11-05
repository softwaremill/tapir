// {cat=WebSocket; effects=Direct; server=Netty}: A WebSocket chat across multiple clients connected to the same server

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.8

package sttp.tapir.examples.websocket

import ox.channels.{Actor, ActorRef, Channel, ChannelClosed, Default, DefaultResult, selectOrClosed}
import ox.{ExitCode, Ox, OxApp, fork, never, releaseAfterScope, supervised}
import sttp.tapir.*
import sttp.tapir.CodecFormat.*
import sttp.tapir.server.netty.sync.{NettySyncServer, OxStreams}

import java.util.UUID
import ox.flow.Flow
import ox.flow.FlowEmit

type ChatMemberId = UUID

case class ChatMember(id: ChatMemberId, channel: Channel[Message])
object ChatMember:
  def create: ChatMember = ChatMember(UUID.randomUUID(), Channel.bufferedDefault[Message])

class ChatRoom:
  private var members: Map[ChatMemberId, ChatMember] = Map()

  def connected(m: ChatMember): Unit =
    members = members + (m.id -> m)
    println(s"Connected: ${m.id}, number of members: ${members.size}")

  def disconnected(m: ChatMember): Unit =
    members = members - m.id
    println(s"Disconnected: ${m.id}, number of members: ${members.size}")

  def incoming(message: Message): Unit =
    println(s"Broadcasting: ${message.v}")
    members = members.flatMap: (id, member) =>
      selectOrClosed(member.channel.sendClause(message), Default(())) match
        case member.channel.Sent() => Some((id, member))
        case _: ChannelClosed =>
          println(s"Channel of member $id closed, removing from members")
          None
        case DefaultResult(_) =>
          println(s"Buffer for member $id full, not sending message")
          Some((id, member))

//

case class Message(v: String) // could be more complex, e.g. JSON including nickname + message
given Codec[String, Message, TextPlain] = Codec.string.map(Message(_))(_.v)

val chatEndpoint = endpoint.get
  .in("chat")
  .out(webSocketBody[Message, TextPlain, Message, TextPlain](OxStreams))

def chatProcessor(a: ActorRef[ChatRoom]): OxStreams.Pipe[Message, Message] = incoming =>
  // returning a flow which, when run, creates a scope to handle the incoming & outgoing messages
  Flow.usingEmit: emit =>
    supervised:
      val member = ChatMember.create

      a.tell(_.connected(member))

      fork:
        incoming.runForeach: msg =>
          a.tell(_.incoming(msg))
        // all incoming messages are processed (= client closed), completing the outgoing channel as well
        member.channel.done()

      // however the scope ends (client close or error), we need to notify the chat room
      releaseAfterScope:
        a.tell(_.disconnected(member))

      FlowEmit.channelToEmit(member.channel, emit)

object WebSocketChatNettySyncServer extends OxApp:
  override def run(args: Vector[String])(using Ox): ExitCode =
    val chatActor = Actor.create(new ChatRoom)
    val chatServerEndpoint = chatEndpoint.handleSuccess(_ => chatProcessor(chatActor))
    val binding = NettySyncServer().addEndpoint(chatServerEndpoint).start()
    releaseAfterScope(binding.stop())
    never
