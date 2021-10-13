package sttp.tapir.tests

import sttp.tapir._

object Files {
  val in_file_out_file: Endpoint[TapirFile, Unit, TapirFile, Any] =
    endpoint.post.in("api" / "echo").in(fileBody).out(fileBody).name("echo file")
}
