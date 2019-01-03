package tapir.tests
import java.io.InputStream

object TestUtil {
  def inputStreamToByteArray(is: InputStream): Array[Byte] = Stream.continually(is.read).takeWhile(_ != -1).map(_.toByte).toArray
}
