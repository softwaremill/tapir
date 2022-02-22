package perfTests

import io.gatling.core.Predef._
import io.gatling.http.Predef._

class AkkaHttpOnlySimulation extends Simulation {
  val httpProtocol = http
    .baseUrl("http://127.0.0.1:8080/akka-http-only")
    .acceptHeader("application/json")
    .contentTypeHeader("json")

  setUp(perfTests.Common.scn.inject(atOnceUsers(100)).protocols(httpProtocol))
}
