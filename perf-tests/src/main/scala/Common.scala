package perfTests

import scala.io.StdIn

object Common {
  def blockServer() = {
    println(Console.BLUE + "Server now online. Please navigate to http://localhost:8080/path0/1\nPress RETURN to stop..." + Console.RESET)
    StdIn.readLine()
  }
}
