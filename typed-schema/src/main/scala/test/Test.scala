package test

class Test {
  import ru.tinkoff.tschema.syntax._

  def api = get |> operation('hello) |> capture[String]('name) |> $$[String]
}
