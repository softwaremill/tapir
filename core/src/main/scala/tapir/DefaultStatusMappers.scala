package tapir

object DefaultStatusMappers {
  def out[O]: O => StatusCode = _ => StatusCodes.Ok
  def error[E]: E => StatusCode = _ => StatusCodes.BadRequest
}
