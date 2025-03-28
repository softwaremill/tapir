import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.pekko.PekkoStreams
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{UriContext, asStringAlways}
import sttp.monad.FutureMonad
import sttp.tapir.generated.TapirGeneratedEndpoints
import sttp.tapir.generated.TapirGeneratedEndpoints.{
  PostCustomContentNegotiationBody0In,
  PostCustomContentNegotiationBody1In,
  PostCustomContentNegotiationBodyErrFull,
  PostCustomContentNegotiationBodyOut,
  PostCustomContentNegotiationBodyOutFull
}
import sttp.tapir.server.stub.TapirStubInterpreter

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.util.Random

class BinaryEndpoints extends AnyFreeSpec with Matchers {
  private implicit val system: ActorSystem = ActorSystem()
  private implicit val materializer: Materializer = Materializer.matFromSystem(system)
  "binary endpoint negotiation works" in {
    case class State(s: String)
    var received: State = State("")
    def iterator: Iterator[ByteString] = new Iterator[ByteString] {
      private var linesToGo: Int = 100
      def hasNext: Boolean = linesToGo > 0
      def next(): ByteString = {
        val nxt = Random.alphanumeric.take(140).mkString
        linesToGo -= 1
        ByteString.fromString(nxt, "utf-8")
      }
    }
    def handleCsv(stream: ByteString): Unit = received = State(stream.utf8String.take(100).mkString)
    def handleSpreadsheet(stream: ByteString): Unit = received = State(stream.utf8String.take(10).mkString)
    def produceCsv(): ByteString = ByteString("CSV")
    def produceSpreadsheet(): ByteString = ByteString("Spreadsheet")
    val route1 =
      TapirGeneratedEndpoints.postCustomContentNegotiation
        .serverLogicSuccess({ body =>
          val partial = body match {
            case PostCustomContentNegotiationBody0In(csv)         => csv.map(handleCsv)
            case PostCustomContentNegotiationBody1In(spreadsheet) => spreadsheet.map(handleSpreadsheet)
          }
          val running = partial.run()
          def out: PostCustomContentNegotiationBodyOut =
            PostCustomContentNegotiationBodyOutFull(
              `text/csv` = () => {
                org.apache.pekko.stream.scaladsl
                  .Source[ByteString](immutable.Iterable.fill(100)(produceCsv()))
                  .concat(Source.future(running).map(_ => ByteString("FIN")))
              },
              `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` = () => {
                org.apache.pekko.stream.scaladsl
                  .Source[ByteString](immutable.Iterable.fill(100)(produceSpreadsheet()))
                  .concat(Source.future(running).map(_ => ByteString("FIN")))
              }
            )

          Future.successful(out)
        })
    val route2 =
      TapirGeneratedEndpoints.postCustomContentNegotiation
        .serverLogic[Future]({ _ =>
          Future.successful(
            Left(
              PostCustomContentNegotiationBodyErrFull(
                `text/csv` = () => "some csv error",
                `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` = () => "some spreadsheet error".getBytes("utf-8")
              )
            )
          )
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
      .post(uri"http://test.com/custom/content-negotiation")
      .response(asStringAlways)
      .header("content-type", "text/csv")
      .header("accept", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
      .streamBody(PekkoStreams)(genSomeLines)
      .response(sttp.client3.asStreamUnsafe(PekkoStreams).map { case Right(s) => s })
      .send[Future, PekkoStreams](stub1)
      .map { resp =>
        resp.code.code shouldEqual 200
        val b = Await.result(resp.body.runFold(Seq.empty[String])((l, a) => l :+ a.utf8String), 5.seconds)
        b shouldEqual (immutable.Iterable.fill(100)(produceSpreadsheet().utf8String).toSeq :+ "FIN")
        received.s.size shouldEqual 100 // csv handler stores state of 100
      }
    def doPost2 = sttp.client3.basicRequest
      .post(uri"http://test.com/custom/content-negotiation")
      .response(asStringAlways)
      .header("content-type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
      .header("accept", "text/csv")
      .streamBody(PekkoStreams)(genSomeLines)
      .response(sttp.client3.asStreamUnsafe(PekkoStreams).map { case Right(s) => s })
      .send[Future, PekkoStreams](stub1)
      .map { resp =>
        resp.code.code shouldEqual 200
        val b = Await.result(resp.body.runFold(Seq.empty[String])((l, a) => l :+ a.utf8String), 5.seconds)
        b shouldEqual (immutable.Iterable.fill(100)(produceCsv().utf8String).toSeq :+ "FIN")
        received.s.size shouldEqual 10 // spreadsheet handler stores state of 10
      }
    def doPost3 = sttp.client3.basicRequest
      .post(uri"http://test.com/custom/content-negotiation")
      .response(asStringAlways)
      .header("content-type", "text/csv")
      .header("accept", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
      .streamBody(PekkoStreams)(genSomeLines)
      .send[Future, PekkoStreams](stub2)
      .map { resp =>
        resp.code.code shouldEqual 400
        resp.body shouldEqual "some spreadsheet error"
      }
    def doPost4 = sttp.client3.basicRequest
      .post(uri"http://test.com/custom/content-negotiation")
      .response(asStringAlways)
      .header("content-type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
      .header("accept", "text/csv")
      .streamBody(PekkoStreams)(genSomeLines)
      .send[Future, PekkoStreams](stub2)
      .map { resp =>
        resp.code.code shouldEqual 400
        resp.body shouldEqual "some csv error"
      }

    Await.result(doPost, 5.seconds)
    Await.result(doPost2, 5.seconds)
    Await.result(doPost3, 5.seconds)
    Await.result(doPost4, 5.seconds)
  }
}
