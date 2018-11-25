package tapir.typelevel.akka
import tapir.typelevel.akka.BinaryPolyFunc.Case
import tapir.typelevel.akka.TupleOps.{AppendOne, FoldLeft}

object TupleOps {
  trait AppendOne[P, S] {
    type Out
    def apply(prefix: P, last: S): Out
  }
  object AppendOne extends TupleAppendOneInstances

  trait FoldLeft[In, T, Op] {
    type Out
    def apply(zero: In, tuple: T): Out
  }
  object FoldLeft extends TupleFoldInstances

  trait Join[P, S] {
    type Out
    def apply(prefix: P, suffix: S): Out
  }
  type JoinAux[P, S, O] = Join[P, S] { type Out = O }
  object Join extends LowLevelJoinImplicits {
    // O(1) shortcut for the Join[Unit, T] case to avoid O(n) runtime in this case
    implicit def join0P[T]: JoinAux[Unit, T, T] =
      new Join[Unit, T] {
        type Out = T
        def apply(prefix: Unit, suffix: T): Out = suffix
      }
    // we implement the join by folding over the suffix with the prefix as growing accumulator
    object Fold extends BinaryPolyFunc {
      implicit def step[T, A](implicit append: AppendOne[T, A]): BinaryPolyFunc.Case[T, A, Fold.type] { type Out = append.Out } =
        at[T, A](append(_, _))
    }
  }
  sealed abstract class LowLevelJoinImplicits {
    implicit def join[P, S](implicit fold: FoldLeft[P, S, Join.Fold.type]): JoinAux[P, S, fold.Out] =
      new Join[P, S] {
        type Out = fold.Out
        def apply(prefix: P, suffix: S): Out = fold(prefix, suffix)
      }
  }
}

/**
  * Allows the definition of binary poly-functions (e.g. for folding over tuples).
  *
  * Note: the poly-function implementation seen here is merely a stripped down version of
  * what Miles Sabin made available with his awesome shapeless library. All credit goes to him!
  */
trait BinaryPolyFunc {
  def at[A, B] = new CaseBuilder[A, B]
  class CaseBuilder[A, B] {
    def apply[R](f: (A, B) ⇒ R) = new BinaryPolyFunc.Case[A, B, BinaryPolyFunc.this.type] {
      type Out = R
      def apply(a: A, b: B) = f(a, b)
    }
  }
}

object BinaryPolyFunc {
  sealed trait Case[A, B, Op] {
    type Out
    def apply(a: A, b: B): Out
  }
}

abstract class TupleFoldInstances {

  type Aux[In, T, Op, Out0] = FoldLeft[In, T, Op] { type Out = Out0 }

  implicit def t0[In, Op]: Aux[In, Unit, Op, In] =
    new FoldLeft[In, Unit, Op] {
      type Out = In
      def apply(zero: In, tuple: Unit) = zero
    }

  implicit def t1[In, A, Op](implicit f: Case[In, A, Op]): Aux[In, Tuple1[A], Op, f.Out] =
    new FoldLeft[In, Tuple1[A], Op] {
      type Out = f.Out
      def apply(zero: In, tuple: Tuple1[A]) = f(zero, tuple._1)
    }

  implicit def t2[In, T1, X, T2, Op](implicit fold: Aux[In, Tuple1[T1], Op, X], f: Case[X, T2, Op]): Aux[In, Tuple2[T1, T2], Op, f.Out] =
    new FoldLeft[In, Tuple2[T1, T2], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple2[T1, T2]) =
        f(fold(zero, Tuple1(t._1)), t._2)
    }
  implicit def t3[In, T1, T2, X, T3, Op](implicit fold: Aux[In, Tuple2[T1, T2], Op, X],
                                         f: Case[X, T3, Op]): Aux[In, Tuple3[T1, T2, T3], Op, f.Out] =
    new FoldLeft[In, Tuple3[T1, T2, T3], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple3[T1, T2, T3]) =
        f(fold(zero, Tuple2(t._1, t._2)), t._3)
    }
  implicit def t4[In, T1, T2, T3, X, T4, Op](implicit fold: Aux[In, Tuple3[T1, T2, T3], Op, X],
                                             f: Case[X, T4, Op]): Aux[In, Tuple4[T1, T2, T3, T4], Op, f.Out] =
    new FoldLeft[In, Tuple4[T1, T2, T3, T4], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple4[T1, T2, T3, T4]) =
        f(fold(zero, Tuple3(t._1, t._2, t._3)), t._4)
    }
  implicit def t5[In, T1, T2, T3, T4, X, T5, Op](implicit fold: Aux[In, Tuple4[T1, T2, T3, T4], Op, X],
                                                 f: Case[X, T5, Op]): Aux[In, Tuple5[T1, T2, T3, T4, T5], Op, f.Out] =
    new FoldLeft[In, Tuple5[T1, T2, T3, T4, T5], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple5[T1, T2, T3, T4, T5]) =
        f(fold(zero, Tuple4(t._1, t._2, t._3, t._4)), t._5)
    }
  implicit def t6[In, T1, T2, T3, T4, T5, X, T6, Op](implicit fold: Aux[In, Tuple5[T1, T2, T3, T4, T5], Op, X],
                                                     f: Case[X, T6, Op]): Aux[In, Tuple6[T1, T2, T3, T4, T5, T6], Op, f.Out] =
    new FoldLeft[In, Tuple6[T1, T2, T3, T4, T5, T6], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple6[T1, T2, T3, T4, T5, T6]) =
        f(fold(zero, Tuple5(t._1, t._2, t._3, t._4, t._5)), t._6)
    }
  implicit def t7[In, T1, T2, T3, T4, T5, T6, X, T7, Op](implicit fold: Aux[In, Tuple6[T1, T2, T3, T4, T5, T6], Op, X],
                                                         f: Case[X, T7, Op]): Aux[In, Tuple7[T1, T2, T3, T4, T5, T6, T7], Op, f.Out] =
    new FoldLeft[In, Tuple7[T1, T2, T3, T4, T5, T6, T7], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple7[T1, T2, T3, T4, T5, T6, T7]) =
        f(fold(zero, Tuple6(t._1, t._2, t._3, t._4, t._5, t._6)), t._7)
    }
  implicit def t8[In, T1, T2, T3, T4, T5, T6, T7, X, T8, Op](
      implicit fold: Aux[In, Tuple7[T1, T2, T3, T4, T5, T6, T7], Op, X],
      f: Case[X, T8, Op]): Aux[In, Tuple8[T1, T2, T3, T4, T5, T6, T7, T8], Op, f.Out] =
    new FoldLeft[In, Tuple8[T1, T2, T3, T4, T5, T6, T7, T8], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple8[T1, T2, T3, T4, T5, T6, T7, T8]) =
        f(fold(zero, Tuple7(t._1, t._2, t._3, t._4, t._5, t._6, t._7)), t._8)
    }
  implicit def t9[In, T1, T2, T3, T4, T5, T6, T7, T8, X, T9, Op](
      implicit fold: Aux[In, Tuple8[T1, T2, T3, T4, T5, T6, T7, T8], Op, X],
      f: Case[X, T9, Op]): Aux[In, Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, T9], Op, f.Out] =
    new FoldLeft[In, Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, T9], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, T9]) =
        f(fold(zero, Tuple8(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8)), t._9)
    }
  implicit def t10[In, T1, T2, T3, T4, T5, T6, T7, T8, T9, X, T10, Op](
      implicit fold: Aux[In, Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, T9], Op, X],
      f: Case[X, T10, Op]): Aux[In, Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10], Op, f.Out] =
    new FoldLeft[In, Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10]) =
        f(fold(zero, Tuple9(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9)), t._10)
    }
  implicit def t11[In, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, X, T11, Op](
      implicit fold: Aux[In, Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10], Op, X],
      f: Case[X, T11, Op]): Aux[In, Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11], Op, f.Out] =
    new FoldLeft[In, Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11]) =
        f(fold(zero, Tuple10(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10)), t._11)
    }
  implicit def t12[In, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, X, T12, Op](
      implicit fold: Aux[In, Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11], Op, X],
      f: Case[X, T12, Op]): Aux[In, Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12], Op, f.Out] =
    new FoldLeft[In, Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12]) =
        f(fold(zero, Tuple11(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11)), t._12)
    }
  implicit def t13[In, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, X, T13, Op](
      implicit fold: Aux[In, Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12], Op, X],
      f: Case[X, T13, Op]): Aux[In, Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13], Op, f.Out] =
    new FoldLeft[In, Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13]) =
        f(fold(zero, Tuple12(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12)), t._13)
    }
  implicit def t14[In, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, X, T14, Op](
      implicit fold: Aux[In, Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13], Op, X],
      f: Case[X, T14, Op]): Aux[In, Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14], Op, f.Out] =
    new FoldLeft[In, Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14]) =
        f(fold(zero, Tuple13(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13)), t._14)
    }
  implicit def t15[In, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, X, T15, Op](
      implicit fold: Aux[In, Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14], Op, X],
      f: Case[X, T15, Op]): Aux[In, Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15], Op, f.Out] =
    new FoldLeft[In, Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15]) =
        f(fold(zero, Tuple14(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14)), t._15)
    }
  implicit def t16[In, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, X, T16, Op](
      implicit fold: Aux[In, Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15], Op, X],
      f: Case[X, T16, Op]): Aux[In, Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16], Op, f.Out] =
    new FoldLeft[In, Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16]) =
        f(fold(zero, Tuple15(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15)), t._16)
    }
  implicit def t17[In, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, X, T17, Op](
      implicit fold: Aux[In, Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16], Op, X],
      f: Case[X, T17, Op]): Aux[In, Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17], Op, f.Out] =
    new FoldLeft[In, Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17]) =
        f(fold(zero, Tuple16(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16)), t._17)
    }
  implicit def t18[In, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, X, T18, Op](
      implicit fold: Aux[In, Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17], Op, X],
      f: Case[X, T18, Op]): Aux[In, Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18], Op, f.Out] =
    new FoldLeft[In, Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18]) =
        f(fold(zero, Tuple17(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17)),
          t._18)
    }
  implicit def t19[In, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, X, T19, Op](
      implicit fold: Aux[In, Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18], Op, X],
      f: Case[X, T19, Op])
    : Aux[In, Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19], Op, f.Out] =
    new FoldLeft[In, Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19]) =
        f(fold(
            zero,
            Tuple18(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18)),
          t._19)
    }
  implicit def t20[In, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, X, T20, Op](
      implicit fold: Aux[In, Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19], Op, X],
      f: Case[X, T20, Op])
    : Aux[In, Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20], Op, f.Out] =
    new FoldLeft[In, Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20]) =
        f(fold(zero,
               Tuple19(t._1,
                       t._2,
                       t._3,
                       t._4,
                       t._5,
                       t._6,
                       t._7,
                       t._8,
                       t._9,
                       t._10,
                       t._11,
                       t._12,
                       t._13,
                       t._14,
                       t._15,
                       t._16,
                       t._17,
                       t._18,
                       t._19)),
          t._20)
    }
  implicit def t21[In, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, X, T21, Op](
      implicit fold: Aux[In, Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20], Op, X],
      f: Case[X, T21, Op])
    : Aux[In, Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21], Op, f.Out] =
    new FoldLeft[In, Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21]) =
        f(fold(zero,
               Tuple20(t._1,
                       t._2,
                       t._3,
                       t._4,
                       t._5,
                       t._6,
                       t._7,
                       t._8,
                       t._9,
                       t._10,
                       t._11,
                       t._12,
                       t._13,
                       t._14,
                       t._15,
                       t._16,
                       t._17,
                       t._18,
                       t._19,
                       t._20)),
          t._21)
    }
  implicit def t22[In, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, X, T22, Op](
      implicit fold: Aux[In,
                         Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21],
                         Op,
                         X],
      f: Case[X, T22, Op])
    : Aux[In, Tuple22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22], Op, f.Out] =
    new FoldLeft[In, Tuple22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22], Op] {
      type Out = f.Out
      def apply(zero: In, t: Tuple22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22]) =
        f(fold(zero,
               Tuple21(t._1,
                       t._2,
                       t._3,
                       t._4,
                       t._5,
                       t._6,
                       t._7,
                       t._8,
                       t._9,
                       t._10,
                       t._11,
                       t._12,
                       t._13,
                       t._14,
                       t._15,
                       t._16,
                       t._17,
                       t._18,
                       t._19,
                       t._20,
                       t._21)),
          t._22)
    }
}

abstract class TupleAppendOneInstances {
  type Aux[P, S, Out0] = AppendOne[P, S] { type Out = Out0 }

  implicit def append0[T1]: Aux[Unit, T1, Tuple1[T1]] =
    new AppendOne[Unit, T1] {
      type Out = Tuple1[T1]
      def apply(prefix: Unit, last: T1): Tuple1[T1] = Tuple1(last)
    }

  implicit def append1[T1, L]: Aux[Tuple1[T1], L, Tuple2[T1, L]] =
    new AppendOne[Tuple1[T1], L] {
      type Out = Tuple2[T1, L]
      def apply(prefix: Tuple1[T1], last: L): Tuple2[T1, L] = Tuple2(prefix._1, last)
    }
  implicit def append2[T1, T2, L]: Aux[Tuple2[T1, T2], L, Tuple3[T1, T2, L]] =
    new AppendOne[Tuple2[T1, T2], L] {
      type Out = Tuple3[T1, T2, L]
      def apply(prefix: Tuple2[T1, T2], last: L): Tuple3[T1, T2, L] = Tuple3(prefix._1, prefix._2, last)
    }
  implicit def append3[T1, T2, T3, L]: Aux[Tuple3[T1, T2, T3], L, Tuple4[T1, T2, T3, L]] =
    new AppendOne[Tuple3[T1, T2, T3], L] {
      type Out = Tuple4[T1, T2, T3, L]
      def apply(prefix: Tuple3[T1, T2, T3], last: L): Tuple4[T1, T2, T3, L] = Tuple4(prefix._1, prefix._2, prefix._3, last)
    }
  implicit def append4[T1, T2, T3, T4, L]: Aux[Tuple4[T1, T2, T3, T4], L, Tuple5[T1, T2, T3, T4, L]] =
    new AppendOne[Tuple4[T1, T2, T3, T4], L] {
      type Out = Tuple5[T1, T2, T3, T4, L]
      def apply(prefix: Tuple4[T1, T2, T3, T4], last: L): Tuple5[T1, T2, T3, T4, L] =
        Tuple5(prefix._1, prefix._2, prefix._3, prefix._4, last)
    }
  implicit def append5[T1, T2, T3, T4, T5, L]: Aux[Tuple5[T1, T2, T3, T4, T5], L, Tuple6[T1, T2, T3, T4, T5, L]] =
    new AppendOne[Tuple5[T1, T2, T3, T4, T5], L] {
      type Out = Tuple6[T1, T2, T3, T4, T5, L]
      def apply(prefix: Tuple5[T1, T2, T3, T4, T5], last: L): Tuple6[T1, T2, T3, T4, T5, L] =
        Tuple6(prefix._1, prefix._2, prefix._3, prefix._4, prefix._5, last)
    }
  implicit def append6[T1, T2, T3, T4, T5, T6, L]: Aux[Tuple6[T1, T2, T3, T4, T5, T6], L, Tuple7[T1, T2, T3, T4, T5, T6, L]] =
    new AppendOne[Tuple6[T1, T2, T3, T4, T5, T6], L] {
      type Out = Tuple7[T1, T2, T3, T4, T5, T6, L]
      def apply(prefix: Tuple6[T1, T2, T3, T4, T5, T6], last: L): Tuple7[T1, T2, T3, T4, T5, T6, L] =
        Tuple7(prefix._1, prefix._2, prefix._3, prefix._4, prefix._5, prefix._6, last)
    }
  implicit def append7[T1, T2, T3, T4, T5, T6, T7, L]: Aux[Tuple7[T1, T2, T3, T4, T5, T6, T7], L, Tuple8[T1, T2, T3, T4, T5, T6, T7, L]] =
    new AppendOne[Tuple7[T1, T2, T3, T4, T5, T6, T7], L] {
      type Out = Tuple8[T1, T2, T3, T4, T5, T6, T7, L]
      def apply(prefix: Tuple7[T1, T2, T3, T4, T5, T6, T7], last: L): Tuple8[T1, T2, T3, T4, T5, T6, T7, L] =
        Tuple8(prefix._1, prefix._2, prefix._3, prefix._4, prefix._5, prefix._6, prefix._7, last)
    }
  implicit def append8[T1, T2, T3, T4, T5, T6, T7, T8, L]
    : Aux[Tuple8[T1, T2, T3, T4, T5, T6, T7, T8], L, Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, L]] =
    new AppendOne[Tuple8[T1, T2, T3, T4, T5, T6, T7, T8], L] {
      type Out = Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, L]
      def apply(prefix: Tuple8[T1, T2, T3, T4, T5, T6, T7, T8], last: L): Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, L] =
        Tuple9(prefix._1, prefix._2, prefix._3, prefix._4, prefix._5, prefix._6, prefix._7, prefix._8, last)
    }
  implicit def append9[T1, T2, T3, T4, T5, T6, T7, T8, T9, L]
    : Aux[Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, T9], L, Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, L]] =
    new AppendOne[Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, T9], L] {
      type Out = Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, L]
      def apply(prefix: Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, T9], last: L): Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, L] =
        Tuple10(prefix._1, prefix._2, prefix._3, prefix._4, prefix._5, prefix._6, prefix._7, prefix._8, prefix._9, last)
    }
  implicit def append10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, L]
    : Aux[Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10], L, Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, L]] =
    new AppendOne[Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10], L] {
      type Out = Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, L]
      def apply(prefix: Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10], last: L): Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, L] =
        Tuple11(prefix._1, prefix._2, prefix._3, prefix._4, prefix._5, prefix._6, prefix._7, prefix._8, prefix._9, prefix._10, last)
    }
  implicit def append11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, L]
    : Aux[Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11], L, Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, L]] =
    new AppendOne[Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11], L] {
      type Out = Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, L]
      def apply(prefix: Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11],
                last: L): Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, L] =
        Tuple12(prefix._1,
                prefix._2,
                prefix._3,
                prefix._4,
                prefix._5,
                prefix._6,
                prefix._7,
                prefix._8,
                prefix._9,
                prefix._10,
                prefix._11,
                last)
    }
  implicit def append12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, L]
    : Aux[Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12], L, Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, L]] =
    new AppendOne[Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12], L] {
      type Out = Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, L]
      def apply(prefix: Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12],
                last: L): Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, L] =
        Tuple13(prefix._1,
                prefix._2,
                prefix._3,
                prefix._4,
                prefix._5,
                prefix._6,
                prefix._7,
                prefix._8,
                prefix._9,
                prefix._10,
                prefix._11,
                prefix._12,
                last)
    }
  implicit def append13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, L]
    : Aux[Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13],
          L,
          Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, L]] =
    new AppendOne[Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13], L] {
      type Out = Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, L]
      def apply(prefix: Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13],
                last: L): Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, L] =
        Tuple14(prefix._1,
                prefix._2,
                prefix._3,
                prefix._4,
                prefix._5,
                prefix._6,
                prefix._7,
                prefix._8,
                prefix._9,
                prefix._10,
                prefix._11,
                prefix._12,
                prefix._13,
                last)
    }
  implicit def append14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, L]
    : Aux[Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14],
          L,
          Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, L]] =
    new AppendOne[Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14], L] {
      type Out = Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, L]
      def apply(prefix: Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14],
                last: L): Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, L] =
        Tuple15(prefix._1,
                prefix._2,
                prefix._3,
                prefix._4,
                prefix._5,
                prefix._6,
                prefix._7,
                prefix._8,
                prefix._9,
                prefix._10,
                prefix._11,
                prefix._12,
                prefix._13,
                prefix._14,
                last)
    }
  implicit def append15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, L]
    : Aux[Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15],
          L,
          Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, L]] =
    new AppendOne[Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15], L] {
      type Out = Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, L]
      def apply(prefix: Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15],
                last: L): Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, L] =
        Tuple16(
          prefix._1,
          prefix._2,
          prefix._3,
          prefix._4,
          prefix._5,
          prefix._6,
          prefix._7,
          prefix._8,
          prefix._9,
          prefix._10,
          prefix._11,
          prefix._12,
          prefix._13,
          prefix._14,
          prefix._15,
          last
        )
    }
  implicit def append16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, L]
    : Aux[Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16],
          L,
          Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, L]] =
    new AppendOne[Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16], L] {
      type Out = Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, L]
      def apply(prefix: Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16],
                last: L): Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, L] =
        Tuple17(
          prefix._1,
          prefix._2,
          prefix._3,
          prefix._4,
          prefix._5,
          prefix._6,
          prefix._7,
          prefix._8,
          prefix._9,
          prefix._10,
          prefix._11,
          prefix._12,
          prefix._13,
          prefix._14,
          prefix._15,
          prefix._16,
          last
        )
    }
  implicit def append17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, L]
    : Aux[Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17],
          L,
          Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, L]] =
    new AppendOne[Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17], L] {
      type Out = Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, L]
      def apply(prefix: Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17],
                last: L): Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, L] =
        Tuple18(
          prefix._1,
          prefix._2,
          prefix._3,
          prefix._4,
          prefix._5,
          prefix._6,
          prefix._7,
          prefix._8,
          prefix._9,
          prefix._10,
          prefix._11,
          prefix._12,
          prefix._13,
          prefix._14,
          prefix._15,
          prefix._16,
          prefix._17,
          last
        )
    }
  implicit def append18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, L]
    : Aux[Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18],
          L,
          Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, L]] =
    new AppendOne[Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18], L] {
      type Out = Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, L]
      def apply(prefix: Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18],
                last: L): Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, L] =
        Tuple19(
          prefix._1,
          prefix._2,
          prefix._3,
          prefix._4,
          prefix._5,
          prefix._6,
          prefix._7,
          prefix._8,
          prefix._9,
          prefix._10,
          prefix._11,
          prefix._12,
          prefix._13,
          prefix._14,
          prefix._15,
          prefix._16,
          prefix._17,
          prefix._18,
          last
        )
    }
  implicit def append19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, L]
    : Aux[Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19],
          L,
          Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, L]] =
    new AppendOne[Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19], L] {
      type Out = Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, L]
      def apply(prefix: Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19],
                last: L): Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, L] =
        Tuple20(
          prefix._1,
          prefix._2,
          prefix._3,
          prefix._4,
          prefix._5,
          prefix._6,
          prefix._7,
          prefix._8,
          prefix._9,
          prefix._10,
          prefix._11,
          prefix._12,
          prefix._13,
          prefix._14,
          prefix._15,
          prefix._16,
          prefix._17,
          prefix._18,
          prefix._19,
          last
        )
    }
  implicit def append20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, L]
    : Aux[Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20],
          L,
          Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, L]] =
    new AppendOne[Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20], L] {
      type Out = Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, L]
      def apply(prefix: Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20],
                last: L): Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, L] =
        Tuple21(
          prefix._1,
          prefix._2,
          prefix._3,
          prefix._4,
          prefix._5,
          prefix._6,
          prefix._7,
          prefix._8,
          prefix._9,
          prefix._10,
          prefix._11,
          prefix._12,
          prefix._13,
          prefix._14,
          prefix._15,
          prefix._16,
          prefix._17,
          prefix._18,
          prefix._19,
          prefix._20,
          last
        )
    }
  implicit def append21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, L]
    : Aux[Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21],
          L,
          Tuple22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, L]] =
    new AppendOne[Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21], L] {
      type Out = Tuple22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, L]
      def apply(prefix: Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21],
                last: L): Tuple22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, L] =
        Tuple22(
          prefix._1,
          prefix._2,
          prefix._3,
          prefix._4,
          prefix._5,
          prefix._6,
          prefix._7,
          prefix._8,
          prefix._9,
          prefix._10,
          prefix._11,
          prefix._12,
          prefix._13,
          prefix._14,
          prefix._15,
          prefix._16,
          prefix._17,
          prefix._18,
          prefix._19,
          prefix._20,
          prefix._21,
          last
        )
    }
}
