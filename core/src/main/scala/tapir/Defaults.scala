package tapir
import java.io.File

object Defaults {
  def statusMapper[O]: O => StatusCode = _ => StatusCodes.Ok // TODO: move to ServerDefaults, make implicit
  def errorStatusMapper[E]: E => StatusCode = _ => StatusCodes.BadRequest
  def createTempFile: () => File = () => File.createTempFile("tapir", "tmp")
}
