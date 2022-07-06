package sttp.tapir.integ.cats

import cats.{Functor, Monad, Semigroup, SemigroupK}
import cats.data.Kleisli
import cats.SemigroupK.Ops
import sttp.tapir.server.ServerRoutes

trait ServerRoutesInstances {

  import cats.implicits._

  type KleisliServerRoutes[F[_], G[_], -REQ, RES] = ServerRoutes[F, Kleisli[G, REQ, RES]]

  implicit def serverRoutesFunctor[F[_]]: Functor[ServerRoutes[F, *]] =
    new Functor[ServerRoutes[F, *]] {
      override def map[A, B](fa: ServerRoutes[F, A])(f: A => B): ServerRoutes[F, B] = fa.map(f)
    }

  implicit def serverRoutesSemigroup[F[_], ROUTES](implicit
      semigroup: Semigroup[ROUTES]
  ): Semigroup[ServerRoutes[F, ROUTES]] =
    (x, y) => x.combine(y)((a, b) => a |+| b)

  implicit def kleisliServerRoutesSemigroupK[F[_]: Monad, G[_]: SemigroupK, A]: SemigroupK[KleisliServerRoutes[F, G, A, *]] =
    new SemigroupK[KleisliServerRoutes[F, G, A, *]] {
      override def combineK[B](
          x: KleisliServerRoutes[F, G, A, B],
          y: KleisliServerRoutes[F, G, A, B]
      ): KleisliServerRoutes[F, G, A, B] =
        x.combine(y)((a, b) => a.combineK(b))
    }

  implicit def toSemigroupKOpsKleisliServerRoutes[F[_], G[_], A, B](
      target: KleisliServerRoutes[F, G, A, B]
  )(implicit tc: SemigroupK[KleisliServerRoutes[F, G, A, *]]): SemigroupK.Ops[KleisliServerRoutes[F, G, A, *], B] =
    new Ops[KleisliServerRoutes[F, G, A, *], B] {
      type TypeClassType = SemigroupK[KleisliServerRoutes[F, G, A, *]]
      val self: KleisliServerRoutes[F, G, A, B] = target
      val typeClassInstance: TypeClassType = tc
    }
}
