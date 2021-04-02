package sttp.tapir.client.http4s

import sttp.tapir.client.tests.ClientBasicTests

class Http4sClientBasicTests extends Http4sClientTests[Any] with ClientBasicTests {
  basicTests()
}
