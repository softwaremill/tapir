package sttp.tapir.client.play

import sttp.tapir.client.tests.ClientBasicTests

import scala.concurrent.Future

class PlayClientBasicTests extends PlayClientTests[Any] with ClientBasicTests[Future] {

  basicTests()

}
