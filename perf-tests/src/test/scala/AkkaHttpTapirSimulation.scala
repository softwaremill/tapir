package perfTests

import io.gatling.core.Predef._
import io.gatling.http.Predef._

class AkkaHttpTapirSimulation extends Simulation {
  var server = new perfTests.AkkaHttpTapirServer()
  val httpProtocol = http.baseUrl("http://127.0.0.1:8080/akka-http-tapir")

  before {
    server.setUp()
  }

  setUp(perfTests.Common.scn.inject(atOnceUsers(1000)).protocols(httpProtocol))

  after {
    server.tearDown()
  }
}
