package sttp.tapir.json.pickler.internal.compiletime

import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

/** Reads the derivation timeout from `-Xmacro-settings:<namespace>.timeout=...`.
  *
  * Hearth's default is deliberately short (5s) so that a runaway derivation fails fast rather than hanging the compiler. A combined Schema
  * + codec derivation over a large ADT graph can legitimately exceed it, hence the override.
  *
  * Accepted formats: `30` (seconds), `30s`, `5000ms`, `1m`.
  */
trait DerivationTimeout { this: hearth.MacroCommons =>

  protected def derivationSettingsNamespace: String

  protected lazy val derivationTimeout: FiniteDuration =
    (for {
      data <- Environment.typedSettings.toOption
      moduleSettings <- data.get(derivationSettingsNamespace)
      timeoutData <- moduleSettings.get("timeout")
      duration <- timeoutData.asInt
        .filter(_ > 0)
        .map(n => FiniteDuration(n.toLong, TimeUnit.SECONDS))
        .orElse(timeoutData.asLong.filter(_ > 0).map(n => FiniteDuration(n, TimeUnit.SECONDS)))
        .orElse(timeoutData.asString.flatMap(parseDurationString))
    } yield duration).getOrElse(DerivationTimeout.Default)

  private def parseDurationString(str: String): Option[FiniteDuration] =
    str.trim match {
      case DerivationTimeout.DurationPattern(num, unit) =>
        val n = num.toLong
        if (n > 0) {
          val tu = unit match {
            case "ms" | "millis" | "milliseconds" => TimeUnit.MILLISECONDS
            case "s" | "second" | "seconds"       => TimeUnit.SECONDS
            case "m" | "minute" | "minutes"       => TimeUnit.MINUTES
          }
          Some(FiniteDuration(n, tu))
        } else {
          Environment.reportWarn(
            s"$derivationSettingsNamespace.timeout: value must be positive, got '$str'. " +
              s"Using default of ${DerivationTimeout.Default.toSeconds}s."
          )
          None
        }
      case _ =>
        Environment.reportWarn(
          s"$derivationSettingsNamespace.timeout: unrecognized format '$str'. " +
            s"Expected formats: 30, 30s, 5000ms, 1m. " +
            s"Using default of ${DerivationTimeout.Default.toSeconds}s."
        )
        None
    }
}

object DerivationTimeout {

  val Default: FiniteDuration = FiniteDuration(5, TimeUnit.SECONDS)

  private[compiletime] val DurationPattern =
    """^\s*(\d+)\s*(ms|millis|milliseconds|s|seconds?|m|minutes?)\s*$""".r
}
