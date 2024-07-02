package sttp.tapir.server.netty.internal.reactivestreams

import org.reactivestreams.Subscriber
import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.Subscription
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.multipart.InterfaceHttpData
import sttp.tapir.server.netty.NettyServerRequest
import org.reactivestreams.Publisher
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.Promise
import scala.concurrent.Future
import org.playframework.netty.http.StreamedHttpRequest
import io.netty.handler.codec.http.multipart.FileUpload

private[internal] class MultiPartSubscriber(initialRequest: HttpRequest) extends Subscriber[HttpContent] {
  private var subscription: Subscription = _
  
  private val multiPartDataFactory = new DefaultHttpDataFactory(true) 
  private var decoder: HttpPostRequestDecoder = _
  private val resultPromise = Promise[Unit]()
  def future: Future[Unit] = resultPromise.future

  // https://github.com/netty/netty/blob/4.1/example/src/main/java/io/netty/example/http/upload/HttpUploadServerHandler.java

  override def onSubscribe(s: Subscription): Unit = {
    decoder = new HttpPostRequestDecoder(multiPartDataFactory, initialRequest)
    this.subscription = s
    s.request(1)
  }


  override def onNext(content: HttpContent): Unit = {
    decoder.offer(content) // TODO can block on I/O if content happens to be data to be written to disk. Also, throws
    while (decoder.hasNext()) { // only fully decoded parts are available with hasNext and Next
      val data: InterfaceHttpData = decoder.next()
      data match {
        case fileUpload: FileUpload => 
          val file = fileUpload.getFile()
          val partName = fileUpload.getName()
          val otherDispositionParams = Map.empty // TODO
          println(s">>>>>>>>>> file upload: ${file}")
      }
      println(s">>>>>>>>>>>>>>>>>>>>>>>>>>> data: $data")
      // TODO
    }
    subscription.request(1)
  }

  override def onError(t: Throwable): Unit = {
    t.printStackTrace() // TODO
    if (decoder != null) decoder.destroy()      
  }

  override def onComplete(): Unit = {
    println(">>>>>>>>>>>>>>>>>>>>>>> MultiPartSubscirber.onComplete")
    if (decoder != null) decoder.destroy() 
    resultPromise.success(())
  }


}

object MultiPartSubscriber {
  // TODO handle maxContentLength
  def processAll(nettyRequest: StreamedHttpRequest): Future[Unit]  = // TODO should return Parts
    val subscriber = new MultiPartSubscriber(nettyRequest)
    nettyRequest.subscribe(subscriber)
    subscriber.future

  def processAllBlocking(nettyRequest: StreamedHttpRequest): Unit =
    Await.result(processAll(nettyRequest), Duration.Inf)
}
