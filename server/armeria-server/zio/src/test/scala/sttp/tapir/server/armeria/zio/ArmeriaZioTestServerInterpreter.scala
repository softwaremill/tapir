package sttp.tapir.server.armeria.zio

import _root_.zio.{Runtime, Task}
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.armeria.{ArmeriaTestServerInterpreter, TapirService}

class ArmeriaZioTestServerInterpreter extends ArmeriaTestServerInterpreter[ZioStreams, Task, ArmeriaZioServerOptions[Task]] {
  import ArmeriaZioTestServerInterpreter._

  override def route(es: List[ServerEndpoint[ZioStreams, Task]], interceptors: Interceptors): TapirService[ZioStreams, Task] = {
    val options: ArmeriaZioServerOptions[Task] = interceptors(ArmeriaZioServerOptions.customiseInterceptors).options
    ArmeriaZioServerInterpreter(options).toService(es)
  }
}

object ArmeriaZioTestServerInterpreter {
  implicit val runtime: Runtime[Any] = Runtime.default
}
