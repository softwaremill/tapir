import scala.io.Source
import io.circe.parser._
import java.io._

object GenerateMimeByExtensionDB {
  def apply(): Unit = {
    val db = Source.fromURL("https://raw.githubusercontent.com/jshttp/mime-db/master/db.json").mkString
    def getOrThrow[E <: Exception, T](i: Either[E, T]): T = i match {
      case Left(e)  => throw e
      case Right(v) => v
    }
    val json = getOrThrow(parse(db))

    val output = new File("core/src/main/resources/mimeByExtensions.txt")
    val pw = new PrintWriter(output)

    try {
      json.hcursor.keys.getOrElse(Nil).foreach { mimeType =>
        val mimeTypeObj = json.hcursor.downField(mimeType)
        val charset = getOrThrow(mimeTypeObj.getOrElse[Option[String]]("charset")(None))
        val extensions = getOrThrow(mimeTypeObj.getOrElse[List[String]]("extensions")(Nil))
        extensions.foreach { ext =>
          pw.println(s"$ext $mimeType${charset.map(";charset=" + _).getOrElse("")}")
        }
      }

      println(s"Done, generated in: ${output.getAbsolutePath}.")
    } finally pw.close()
  }
}
