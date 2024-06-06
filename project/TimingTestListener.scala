import sbt._
import sbt.Keys._
import sbt.testing.{Status, Event, EventHandler, Logger => TLogger}

object TimingTestListener extends TestsListener {
  def now(): Long = System.nanoTime() / 1000000

  override def startGroup(name: String): Unit = {
    println(s"[TESTINFO] Starting test group: $name,${now()}")
  }

  override def testEvent(event: TestEvent): Unit = {
    if (event.detail.isEmpty)
      println(s"[TESTINFO] Test event: ${event},${now()}")
    else
      event.detail.foreach { e =>
        val status = if (e.status() == Status.Success) "PASSED" else "FAILED"
        println(s"[TESTINFO] Test ${e.fullyQualifiedName()} $status in ${e.duration()} ms,${now()}")
      }
  }

  override def endGroup(name: String, t: Throwable): Unit = {
    println(s"[TESTINFO] Test group $name failed with ${t.getMessage},${now()}")
  }

  override def endGroup(name: String, result: TestResult): Unit = {
    println(s"[TESTINFO] Finished test group: $name result: $result,${now()}")
  }

  override def doInit(): Unit = { 
  }
    
  override def doComplete(finalResult: TestResult): Unit = { 
   () 
  }
}
