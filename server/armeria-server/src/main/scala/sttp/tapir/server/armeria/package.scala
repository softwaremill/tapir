package sttp.tapir.server

import com.linecorp.armeria.common.HttpData
import com.linecorp.armeria.common.stream.StreamMessage
import sttp.capabilities.armeria.ArmeriaStreams

import scala.concurrent.Future

package object armeria {

  type ArmeriaServerRoutes = ServerRoutes[Future, TapirService[ArmeriaStreams, Future]]

  private[armeria] type ArmeriaResponseType = Either[StreamMessage[HttpData], HttpData]
}
