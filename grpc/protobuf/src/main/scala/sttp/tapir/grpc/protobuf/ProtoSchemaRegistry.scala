package sttp.tapir.grpc.protobuf

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import com.typesafe.scalalogging.StrictLogging
import sttp.tapir.grpc.protobuf.model._

//FIXME find a better way for saving these schemas
abstract class ProtoSchemaRegistry extends App with StrictLogging {
  def renderer: ProtoRenderer
  def path: String
  def proto: Protobuf

  def register(): Unit = {
    val renderedProto = renderer.render(proto)

    Files.write(
      Paths.get(path),
      renderedProto.getBytes(StandardCharsets.UTF_8),
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING
    )
  }

}
