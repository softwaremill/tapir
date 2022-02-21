package perfTests

import io.gatling.core.Predef._
import io.gatling.http.Predef._

class AkkaHttpTapirSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl("http://127.0.0.1:8080/akka-http-tapir")
    .acceptHeader("application/json")
    .contentTypeHeader("json")

  val scn = scenario("get users")
    .exec(http("first user")
      .get("/4"))
    .pause(1)
    .exec(http("first user")
      .get("/35"))

  setUp(scn.inject(atOnceUsers(10)).protocols(httpProtocol))
}
