package sttp.tapir.client.tests

import sttp.monad.MonadError
import sttp.tapir.tests.EffectfulMappings._
import sttp.tapir.tests.{Fruit, FruitAmount}

trait ClientEffectfulMappingsTests[F[_]] { this: ClientTests[Any, F] =>
  implicit def monad: MonadError[F]

  def tests(): Unit = {
    testClient(in_f_query_out_string[F], Fruit("apple"), Right("fruit: apple"))
    testClient(in_f_query_query_out_string[F], (Fruit("apple"), 10), Right("fruit: apple 10"))
    testClient(in_f_mapped_query_query_out_string[F], FruitAmount("apple", 10), Right("fruit: apple 10"))
    testClient(in_query_out_f_string[F], "apple", Right(Fruit("fruit: apple")))
    testClient(in_query_out_f_string_int[F], "apple", Right((Fruit("fruit: apple"), 5)))
    testClient(in_query_out_f_mapped_string_int[F], "apple", Right(FruitAmount("fruit: apple", 5)))
  }
}
