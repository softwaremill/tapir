package sttp.tapir

import org.scalajs.dom.File

package object fileTest {
  val in_file_out_file: Endpoint[File, Unit, File, Any] =
    endpoint.post.in("api" / "echo").in(fileBody).out(fileBody).name("echo file")
}
