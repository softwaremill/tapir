package perfTests

import io.gatling.core.Predef._
import io.gatling.http.Predef._

object Common {
  val scn = scenario("get jsons")
    .exec(http("first json")
      .get("/4"))
    .pause(1)
    .exec(http("second json")
      .get("/35"))
}
