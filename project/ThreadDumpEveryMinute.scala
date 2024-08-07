import java.lang.management.ManagementFactory

object ThreadDumpEveryMinute {
  new Thread() {
    override def run() = {
      while (true) {
        println("[XXX] thread dump coming up in 10 minutes ... ")
        Thread.sleep(1000 * 60 * 10)
        val threadDump = new StringBuffer(System.lineSeparator)
        val threadMXBean = ManagementFactory.getThreadMXBean
        for (threadInfo <- threadMXBean.dumpAllThreads(true, true)) {
          threadDump.append(threadInfo.toString)
        }
        println("--- XXX ---")
        println(threadDump.toString)
        println("--- XXX ---")
      }
    }
  }.start()
}
