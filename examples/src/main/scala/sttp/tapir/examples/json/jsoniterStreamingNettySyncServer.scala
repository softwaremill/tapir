// {cat=JSON; effects=Direct; server=Netty; JSON=jsoniter; docs=Swagger UI}: Receive JSON, parse it in a streaming way, expose documentation

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.38
//> using dep com.softwaremill.sttp.tapir::tapir-jsoniter-scala:1.11.38
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.38
//> using dep com.softwaremill.sttp.tapir::tapir-swagger-ui-bundle:1.11.38
//> using dep ch.qos.logback:logback-classic:1.5.18
//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:2.36.7

package sttp.tapir.examples.json

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import org.slf4j.LoggerFactory
import ox.flow.Flow
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.server.netty.sync.NettySyncServer
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import java.io.InputStream
import ox.either
import ox.either.*

@main def jsoniterStreamingNettySyncServer(): Unit =
  val logger = LoggerFactory.getLogger(this.getClass)
  case class Person(name: String, age: Int) derives ConfiguredJsonValueCodec

  val streamPeopleEndpoint = endpoint.post
    .in("count")
    .in(
      // Constructing the body directly using the EndpointIO.Body data structure, instead of the helper methods (such
      // as inputStreamBody or binaryBody), so that we can provide a custom codec format (JSON) and schema (a person
      // array). That way, the documentation is properly generated, even though the format is only enforced by the
      // endpoint's logic, not by Tapir's library code.
      EndpointIO.Body(
        RawBodyType.InputStreamBody,
        Codec.id(CodecFormat.Json(), summon[Schema[List[Person]]].as[InputStream]),
        EndpointIO.Info.empty
      )
    )
    .errorOut(stringBody)
    .out(stringBody)
    .handle(is =>
      either:
        val received = Flow
          .usingEmit(emit =>
            scanJsonArrayFromStream[Person](is) { p =>
              emit(p)
              true
            }
          )
          .map(_ => 1)
          // Here, arbitrary streaming transformations can be applied; the entire request body is never
          // fully accumulated in memory.
          .runFold(0)(_ + _)
          // Since the parsing is done in the endpoint's logic, any exceptions which are normally handled by Tapir's
          // JSON integrations, and then by the decode failure handler, need to be done manually here: Tapir considers
          // the body as already parsed & validated, even though it's only an InputStream.
          .catching[JsonReaderException]
          .ok()

        s"Received data for $received people"
      .left.map(_.getMessage())
    )

  val docsEndpoints = SwaggerInterpreter().fromServerEndpoints(List(streamPeopleEndpoint), "People streaming", "1.0.0")

  logger.info("Open http://localhost:8080/docs to browse the docs")
  NettySyncServer().addEndpoint(streamPeopleEndpoint).addEndpoints(docsEndpoints).startAndWait()
