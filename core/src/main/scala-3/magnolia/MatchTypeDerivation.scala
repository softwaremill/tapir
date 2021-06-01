package magnolia

import scala.deriving.Mirror
import scala.compiletime.*
import scala.reflect.*
import magnolia.*
import Macro.*
import sttp.tapir.typelevel.MatchType
import scala.quoted.*
import sttp.tapir.typelevel.MatchTypeMacros
import java.util.concurrent.atomic.AtomicReference

trait MatchTypeDerivation {
    import MatchTypeDerivation.*

    type Typeclass[T] = MatchType[T]

    def join[T](ctx: CaseClass[Typeclass, T]): Typeclass[T]

    inline def derivedMirrorProduct[A](product: Mirror.ProductOf[A]): Typeclass[A] =
        val parameters = IArray(getParams[A, product.MirroredElemLabels, product.MirroredElemTypes](
            paramAnns[A].to(Map), paramTypeAnns[A].to(Map), repeated[A].to(Map))*)
    
        val caseClass = new CaseClass[Typeclass, A](typeInfo[A], isObject[A], isValueClass[A], parameters,
            IArray(anns[A]*), IArray[Any](typeAnns[A]*)):
        
            def construct[PType](makeParam: Param => PType)(using ClassTag[PType]): A =
                product.fromProduct(Tuple.fromArray(this.params.map(makeParam(_)).to(Array)))

            def rawConstruct(fieldValues: Seq[Any]): A = product.fromProduct(Tuple.fromArray(fieldValues.to(Array)))

            def constructEither[Err, PType: ClassTag](makeParam: Param => Either[Err, PType]): Either[List[Err], A] =
                params.map(makeParam(_)).to(Array).foldLeft[Either[List[Err], Array[PType]]](Right(Array())) {
                case (Left(errs), Left(err))    => Left(errs ++ List(err))
                case (Right(acc), Right(param)) => Right(acc ++ Array(param))
                case (errs@Left(_), _)          => errs
                case (_, Left(err))             => Left(List(err))
                }.map { params => product.fromProduct(Tuple.fromArray(params)) }

            def constructMonadic[M[_]: Monadic, PType: ClassTag](makeParam: Param => M[PType]): M[A] =
                summon[Monadic[M]].map {
                params.map(makeParam(_)).to(Array).foldLeft(summon[Monadic[M]].point(Array())) {
                    (accM, paramM) => summon[Monadic[M]].flatMap(accM) { acc =>
                    summon[Monadic[M]].map(paramM)(acc ++ List(_))
                    }
                }
                } { params => product.fromProduct(Tuple.fromArray(params)) }

        join(caseClass)        

    inline def getParams[T, Labels <: Tuple, Params <: Tuple]
                        (annotations: Map[String, List[Any]], typeAnnotations: Map[String, List[Any]],
                        repeated: Map[String, Boolean], idx: Int = 0): List[CaseClass.Param[Typeclass, T]] =

        inline erasedValue[(Labels, Params)] match
        case _: (EmptyTuple, EmptyTuple) =>
            Nil
        case _: ((l *: ltail), (p *: ptail)) =>
            val label = constValue[l].asInstanceOf[String]
            // lazy val typeclass = CallByNeed(summonFrom {
            //     case t: Typeclass[`p`] => t
            //     case m: Mirror.Of[`p`] => derived[p](using m)
            // })

            lazy val typeclass = CallByNeed(summonCached[p])

            CaseClass.Param[Typeclass, T, p](label, idx, repeated.getOrElse(label, false), typeclass,
                CallByNeed(None), IArray.from(annotations.getOrElse(label, List())),
                IArray.from(typeAnnotations.getOrElse(label, List()))) ::
                getParams[T, ltail, ptail](annotations, typeAnnotations, repeated, idx + 1)

    def split[T](ctx: SealedTrait[Typeclass, T]): Typeclass[T]

    transparent inline def subtypes[T, SubtypeTuple <: Tuple]
                                    (m: Mirror.SumOf[T], idx: Int = 0): List[SealedTrait.Subtype[Typeclass, T, _]] =
        inline erasedValue[SubtypeTuple] match
        case _: EmptyTuple =>
            Nil
        case _: (s *: tail) =>
            lazy val typeclass = CallByNeed(summonCached[s])    

            new SealedTrait.Subtype(typeInfo[s], IArray[Any](), IArray.from(paramTypeAnns[T]), idx,
                typeclass, x => m.ordinal(x) == idx,
                _.asInstanceOf[s & T]) :: subtypes[T, tail](m, idx + 1)

    inline def derivedMirrorSum[A](sum: Mirror.SumOf[A]): Typeclass[A] =
        val sealedTrait = SealedTrait(typeInfo[A], IArray(subtypes[A, sum.MirroredElemTypes](sum)*),
            IArray[Any](), IArray(paramTypeAnns[A]*))
        split(sealedTrait) 

    inline def derivedMirror[A](using mirror: Mirror.Of[A]): Typeclass[A] = inline mirror match
        case sum: Mirror.SumOf[A]         => derivedMirrorSum[A](sum)
        case product: Mirror.ProductOf[A] => derivedMirrorProduct[A](product)

    inline def summonCached[A]: MatchType[A] = ${summonCachedImpl[A]}

    inline def derived[A](using Mirror.Of[A]): Typeclass[A] = derivedMirror[A]

}

object MatchTypeDerivation {
    import scala.collection.mutable
    import scala.quoted.*
    import scala.quoted.*

  val deriveCache: AtomicReference[scala.collection.mutable.Map[String, Any]] = new AtomicReference(mutable.Map.empty)

    def summonCachedImpl[A: Type](using q: Quotes): Expr[MatchType[A]] = {
        import q.reflect.{MatchType => MT, *}

        lazy val genMatchType = '{ 
            MatchType.gen[A](
                ${Expr.summon[ClassTag[A]].get},
                ${Expr.summon[Mirror.Of[A]].get}
            )
        }

        val inProgress = deriveCache.get.asInstanceOf[mutable.Map[String, Term]]
        deriveCache.set(inProgress.asInstanceOf[mutable.Map[String, Any]])
        val tpe = TypeRepr.of[A]
        val symbol = tpe.typeSymbol
        val name = symbol.name

        '{  
            ${Expr.summon[MatchType[A]] match {
                case Some(t) => 
                println(s"FOUND [$name] = [$t]")
                inProgress.put(name, t.asTerm)
                t
                case None => 
                println(s"NOT FOUND [$name]")
                inProgress.get(name) match {
                    case Some(t) => 
                        println(s"FOUND IN CACHE [$name] = [$t]")
                        t.asExprOf[MatchType[A]]
                    case None => 
                        println(s"NOT FOUND IN CACHE [$name]")    
                        val t = genMatchType
                        println(s"DERIVED [$name]")
                        inProgress.put(name, t.asTerm)
                        t
                }
                
            }}
        }
        
    }
}