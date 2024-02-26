package sttp.tapir.perf

import org.HdrHistogram.Histogram

import java.io.{FileOutputStream, PrintStream}
import java.nio.file.Paths
import scala.util.{Failure, Success, Using}

object HistogramPrinter {
  def saveToFile(histogram: Histogram, histogramName: String): Unit = {
    val baseDir = System.getProperty("user.dir")
    val targetFilePath = Paths.get(baseDir).resolve(s"$histogramName-${System.currentTimeMillis()}")
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
