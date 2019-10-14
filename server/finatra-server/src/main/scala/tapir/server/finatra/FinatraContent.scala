package tapir.server.finatra
import com.twitter.io.{Buf, Reader}

sealed trait FinatraContent
case class FinatraContentBuf(buf: Buf) extends FinatraContent
case class FinatraContentReader(reader: Reader[Buf]) extends FinatraContent
