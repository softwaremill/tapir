import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.pekko.PekkoStreams
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{Response, UriContext, asStringAlways}
import sttp.monad.FutureMonad
import sttp.tapir.generated.TapirGeneratedEndpoints
import sttp.tapir.server.stub.TapirStubInterpreter

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.util.Random

class BinaryEndpoints extends AnyFreeSpec with Matchers {
  private implicit val system: ActorSystem = ActorSystem()
  private implicit val materializer: Materializer = Materializer.matFromSystem(system)
  "binary endpoints work" in {
    val respQueue = mutable.Queue.empty[String]
    def iterator: Iterator[ByteString] = new Iterator[ByteString] {
      private var linesToGo: Int = 100
      def hasNext: Boolean = linesToGo > 0
      def next(): ByteString = {
        val nxt = Random.alphanumeric.take(40).mkString
        linesToGo -= 1
        ByteString.fromString(nxt, "utf-8")
      }
    }
    val route1 =
      TapirGeneratedEndpoints.postBinaryTest.serverLogicSuccess[Future]({ source: Source[ByteString, Any] =>
        source
          .map(bs => respQueue.append(bs.utf8String.reverse))
          .run()
          .map(_ => "ok")
      })
    val route2 =
      TapirGeneratedEndpoints.getBinaryTest.serverLogicSuccess[Future]({ _ =>
        Future.successful(org.apache.pekko.stream.scaladsl.Source[ByteString]({
          respQueue.map(ByteString.fromString).toSeq
        }))
      })

    val stub1 = TapirStubInterpreter(SttpBackendStub[Future, PekkoStreams](new FutureMonad()))
      .whenServerEndpoint(route1)
      .thenRunLogic()
      .backend()
    val stub2 = TapirStubInterpreter(SttpBackendStub[Future, PekkoStreams](new FutureMonad()))
      .whenServerEndpoint(route2)
      .thenRunLogic()
      .backend()

    def genSomeLines: Source[ByteString, Any] = Source.fromIterator(() => iterator)

    def doPost = sttp.client3.basicRequest
      .post(uri"http://test.com/binary/test")
      .response(asStringAlways)
      .streamBody(PekkoStreams)(genSomeLines)
      .send(stub1)
      .map { resp =>
        resp.code.code === 200
        resp.body === Right("ok")
      }

    def doGet: Future[Response[Source[ByteString, Any]]] = sttp.client3.basicRequest
      .get(uri"http://test.com/binary/test")
      .response(sttp.client3.asStreamUnsafe(PekkoStreams).map { case Right(s) => s })
      .send[Future, PekkoStreams](stub2)

    Await.result(doPost, 5.seconds)
    respQueue.size shouldEqual 100
    val orig = respQueue.toSeq
    val b = Await.result(doGet.flatMap(_.body.runFold(Seq.empty[String])((l, a) => l :+ a.utf8String)), 5.seconds)
    b.size shouldEqual 100
    b shouldEqual orig
  }
}
