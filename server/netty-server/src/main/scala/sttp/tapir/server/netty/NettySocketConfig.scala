package sttp.tapir.server.netty

/** Netty configuration, used by [[NettyFutureServer]] and other server implementations to configure the networking layer, the Netty
 * processing pipeline, and start & stop the server.
 *
 * @param sendBuffer
 *   The size of the socket send buffer.
 *   The value of this socket option is an Integer that is the size of the socket send buffer in bytes. The socket send buffer is an output buffer used by the networking implementation. It may need to be increased for high-volume connections. The value of the socket option is a hint to the implementation to size the buffer and the actual size may differ. The socket option can be queried to retrieve the actual size.
 *   For datagram-oriented sockets, the size of the send buffer may limit the size of the datagrams that may be sent by the socket. Whether datagrams larger than the buffer size are sent or discarded is system dependent.
 *   The initial/default size of the socket send buffer and the range of allowable values is system dependent although a negative size is not allowed. An attempt to set the socket send buffer to larger than its maximum size causes it to be set to its maximum size.
 *
 * @param receiveBuffer
 *   The size of the socket receive buffer.
 *   The value of this socket option is an Integer that is the size of the socket receive buffer in bytes. The socket receive buffer is an input buffer used by the networking implementation. It may need to be increased for high-volume connections or decreased to limit the possible backlog of incoming data. The value of the socket option is a hint to the implementation to size the buffer and the actual size may differ.
 *   For datagram-oriented sockets, the size of the receive buffer may limit the size of the datagrams that can be received. Whether datagrams larger than the buffer size can be received is system dependent. Increasing the socket receive buffer may be important for cases where datagrams arrive in bursts faster than they can be processed.
 *   In the case of stream-oriented sockets and the TCP/IP protocol, the size of the socket receive buffer may be used when advertising the size of the TCP receive window to the remote peer.
 *
 * @param tcpNoDelay
 *   Disable the Nagle algorithm.
 *   The value of this socket option is a Boolean that represents whether the option is enabled or disabled. The socket option is specific to stream-oriented sockets using the TCP/IP protocol. TCP/IP uses an algorithm known as The Nagle Algorithm to coalesce short segments and improve network efficiency.
 *
 * @param reuseAddress
 *   Re-use address.The value of this socket option is a Boolean that represents whether the option is enabled or disabled. The exact semantics of this socket option are socket type and system dependent.
 *   In the case of stream-oriented sockets, this socket option will usually determine whether the socket can be bound to a socket address when a previous connection involving that socket address is in the TIME_WAIT state. On implementations where the semantics differ, and the socket option is not required to be enabled in order to bind the socket when a previous connection is in this state, then the implementation may choose to ignore this option.
 *   For datagram-oriented sockets the socket option is used to allow multiple programs bind to the same address. This option should be enabled when the socket is to be used for Internet Protocol (IP) multicasting.
 *
 * @param typeOfService
 *   The Type of Service (ToS) octet in the Internet Protocol (IP) header.
 *   The value of this socket option is an Integer representing the value of the ToS octet in IP packets sent by sockets to an IPv4 socket. The interpretation of the ToS octet is network specific and is not defined by this class. Further information on the ToS octet can be found in RFC 1349 and RFC 2474. The value of the socket option is a hint. An implementation may ignore the value, or ignore specific values.
 *   The initial/default value of the TOS field in the ToS octet is implementation specific but will typically be 0. For datagram-oriented sockets the option may be configured at any time after the socket has been bound. The new value of the octet is used when sending subsequent datagrams. It is system dependent whether this option can be queried or changed prior to binding the socket.
*/
case class NettySocketConfig(
  sendBuffer: Option[Int],
  receiveBuffer: Option[Int],
  tcpNoDelay: Boolean,
  reuseAddress: Boolean,
  typeOfService: Option[Int]
) {

  def withSendBuffer(i: Int) = copy(sendBuffer = Some(i))
  def withNoSendBuffer = copy(sendBuffer = None)
  def withReceiveBuffer(i: Int) = copy(receiveBuffer = Some(i))
  def withNoReceiveBuffer = copy(receiveBuffer = None)
  def withTcpNoDelay = copy(tcpNoDelay = true)

  def withNoTcpNoDelay = copy(tcpNoDelay = false)
  def withReuseAddress = copy(reuseAddress = true)

  def withNoReuseAddress = copy(reuseAddress = false)
  def withTypeOfService(i: Int) = copy(typeOfService = Some(i))
  def withNoTypeOfService = copy(typeOfService = None)

}

object NettySocketConfig {

  def default: NettySocketConfig = NettySocketConfig(
    sendBuffer = None,
    receiveBuffer = None,
    tcpNoDelay = false,
    reuseAddress = false,
    typeOfService = None
  )

}
