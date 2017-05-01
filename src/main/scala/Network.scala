/*
 * Copyright (C) 2009-2017 Lightbend Inc. <http://www.lightbend.com>
 */

package dk.diku.blockchain

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.BinaryMessage
import akka.stream.scaladsl.Sink

import scala.io.StdIn

class Network(port: Int)(implicit system: ActorSystem) {

  import akka.stream.ActorMaterializer
  import akka.stream.scaladsl.{ Source, Flow }
  import akka.http.scaladsl.Http
  import akka.http.scaladsl.model.ws.UpgradeToWebSocket
  import akka.http.scaladsl.model.ws.{ TextMessage, Message }
  import akka.http.scaladsl.model.{ HttpResponse, Uri, HttpRequest }
  import akka.http.scaladsl.model.HttpMethods._

  implicit val materializer = ActorMaterializer()

  val greeterWebSocketService =
    Flow[Message]
      .mapConcat {
      case tm: TextMessage => TextMessage(Source.single("Hello ") ++ tm.textStream) :: Nil
      case bm: BinaryMessage =>
        bm.dataStream.runWith(Sink.ignore)
        Nil
    }

  val requestHandler: HttpRequest => HttpResponse = {
    case req @ HttpRequest(GET, Uri.Path("/"), _, _, _) =>
      req.header[UpgradeToWebSocket] match {
        case Some(upgrade) => upgrade.handleMessages(greeterWebSocketService)
        case None          => HttpResponse(400, entity = "Not a valid websocket request!")
      }
    case r: HttpRequest =>
      r.discardEntityBytes()
      HttpResponse(404, entity = "Unknown resource!")
  }

  val bindingFuture =
    Http().bindAndHandleSync(requestHandler, interface = "0.0.0.0", port = 9000)

  def exit = {
    import system.dispatcher
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }

}
