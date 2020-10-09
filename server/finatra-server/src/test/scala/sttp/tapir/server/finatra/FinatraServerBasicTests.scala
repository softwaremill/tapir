package sttp.tapir.server.finatra

import com.twitter.util.Future
import sttp.tapir.server.tests.ServerBasicTests

class FinatraServerBasicTests extends FinatraServerTests with ServerBasicTests[Future, FinatraRoute] {
  basicTests()
}
