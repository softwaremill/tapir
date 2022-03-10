package perfTests

import sttp.tapir._
import scala.io.StdIn

object Common {
  def genTapirEndpoint(n: Int) = endpoint.get
    .in("path" + n.toString)
    .in(path[Int]("id"))
    .errorOut(stringBody)
    .out(stringBody)

  def blockServer() = {
    println(Console.BLUE + "Server now online. Please navigate to http://localhost:8080/path0/1\nPress RETURN to stop..." + Console.RESET)
    StdIn.readLine()
    println("Server terminated")
  }
}
