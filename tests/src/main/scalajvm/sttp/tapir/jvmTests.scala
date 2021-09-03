package sttp.tapir

import java.io.File

package object jvmTests {
  val in_file_out_file: Endpoint[File, Unit, File, Any] =
    endpoint.post.in("api" / "echo").in(fileBody).out(fileBody).name("echo file")

}
