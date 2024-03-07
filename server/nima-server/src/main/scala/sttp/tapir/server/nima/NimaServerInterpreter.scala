package sttp.tapir.server.nima

import io.helidon.http.Status
import io.helidon.webserver.http.{Handler, ServerRequest => HelidonServerRequest, ServerResponse => HelidonServerResponse}
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interpreter.{BodyListener, FilterServerEndpoints, ServerInterpreter}
import sttp.tapir.server.nima.internal.{NimaBodyListener, NimaRequestBody, NimaServerRequest, NimaToResponseBody, idMonad}

import java.io.InputStream

trait NimaServerInterpreter {
  def nimaServerOptions: NimaServerOptions

  def toHandler(ses: List[ServerEndpoint[Any, Id]]): Handler = {
    val filteredEndpoints = FilterServerEndpoints[Any, Id](ses)
    val requestBody = new NimaRequestBody(nimaServerOptions.createFile)
    val responseBody = new NimaToResponseBody
    val interceptors = RejectInterceptor.disableWhenSingleEndpoint(nimaServerOptions.interceptors, ses)

    (helidonRequest: HelidonServerRequest, helidonResponse: HelidonServerResponse) => {
      implicit val bodyListener: BodyListener[Id, InputStream] = new NimaBodyListener(helidonResponse)

      val serverInterpreter = new ServerInterpreter[Any, Id, InputStream, NoStreams](
        filteredEndpoints,
        requestBody,
        responseBody,
        interceptors,
        nimaServerOptions.deleteFile
      )

      serverInterpreter(NimaServerRequest(helidonRequest)) match {
        case RequestResult.Response(tapirResponse) =>
          helidonResponse.status(Status.create(tapirResponse.code.code))
          tapirResponse.headers.groupBy(_.name).foreach { case (name, headers) =>
            helidonResponse.header(name, headers.map(_.value): _*)
          }

          tapirResponse.body.fold(ifEmpty = helidonResponse.send()) { tapirInputStream =>
            val helidonOutputStream = helidonResponse.outputStream()
            try {
              val _ = tapirInputStream.transferTo(helidonOutputStream)
            } finally {
              helidonOutputStream.close()
            }
          }

        // If endpoint matching fails, we return control to Nima
        case RequestResult.Failure(_) =>
          helidonResponse.next()()
      }
    }
  }
}

object NimaServerInterpreter {
  def apply(serverOptions: NimaServerOptions = NimaServerOptions.Default): NimaServerInterpreter =
    new NimaServerInterpreter {
      override def nimaServerOptions: NimaServerOptions = serverOptions
    }
}
