package sttp.tapir.grpc.protobuf

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import com.typesafe.scalalogging.StrictLogging
import sttp.tapir.AnyEndpoint
import sttp.tapir.grpc.protobuf.model._

trait ProtoSchemaGenerator extends App with StrictLogging {
  def path: String
  def endpoints: Iterable[AnyEndpoint]
  def packageName: PackageName

  def generate(): Unit = {
    logger.info(s"Generating proto file")

    val renderer: ProtoRenderer = new ProtoRenderer()
    val interpreter = new ProtobufInterpreter(new EndpointToProtobufMessage(), new EndpointToProtobufService())
    val proto = interpreter.toProtobuf(endpoints, Some(packageName))

    val renderedProto = renderer.render(proto)

    logger.debug(s"Generated protobuf structure: [$renderedProto]")
    logger.info(s"Writing proto file to the path [$path]")

    Files.write(
      Paths.get(path),
      renderedProto.getBytes(StandardCharsets.UTF_8),
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING
    )
  }

}
