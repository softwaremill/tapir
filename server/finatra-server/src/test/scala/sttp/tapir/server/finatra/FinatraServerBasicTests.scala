package sttp.tapir.server.finatra

import com.twitter.util.Future
import sttp.tapir.server.tests.ServerBasicTests
import sttp.tapir.tests.PortCounter

class FinatraServerBasicTests extends FinatraServerTests with ServerBasicTests[Future, FinatraRoute] {
  basicTests()

  override val portCounter: PortCounter = new PortCounter(32000)
}
