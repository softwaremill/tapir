package sttp.tapir.server.finatra.cats

import cats.effect.IO
import sttp.tapir.server.finatra.FinatraRoute
import sttp.tapir.server.tests.ServerBasicTests

class FinatraServerCatsBasicTests extends FinatraServerCatsTests with ServerBasicTests[IO, FinatraRoute] {
  basicTests()
}
