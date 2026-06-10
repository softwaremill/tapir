package sttp.tapir.perf

import org.HdrHistogram.Histogram

import java.io.{FileOutputStream, PrintStream}
import java.nio.file.Paths
import scala.util.{Failure, Success, Using}
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime

object HistogramPrinter {
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm_ss")

  def saveToFile(histogram: Histogram, histogramName: String): Unit = {
    val currentTime = LocalDateTime.now().format(formatter)
    val baseDir = System.getProperty("user.dir")
    val targetFilePath = Paths.get(baseDir).resolve(s"$histogramName-$currentTime")
    Using.Manager { use =>
      val fos = use(new FileOutputStream(targetFilePath.toFile))
      val ps = use(new PrintStream(fos))
      histogram.outputPercentileDistribution(System.out, 1.0)
      histogram.outputPercentileDistribution(ps, 1.0)
    } match {
      case Success(_) =>
        println(s"******* Histogram saved to $targetFilePath")
      case Failure(ex) =>
        ex.printStackTrace
    }
  }
}
