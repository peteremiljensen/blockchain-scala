/*
 * Copyright (C) 2009-2017 Lightbend Inc. <http://www.lightbend.com>
 */

package dk.diku.blockchain

import akka.actor._
import akka.event.LoggingReceive
import akka.http.scaladsl.model.ws.BinaryMessage
import akka.stream.scaladsl.Sink

import akka.stream._
import akka.stream.scaladsl.{ Source, Flow }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.UpgradeToWebSocket
import akka.http.scaladsl.model.ws.{ TextMessage, Message }
import akka.http.scaladsl.model.{ HttpResponse, Uri, HttpRequest }
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.model.HttpMethods._
import akka.NotUsed

class Network(port: Int)(implicit system: ActorSystem) {

  implicit val materializer = ActorMaterializer()

  val connectionManager = system.actorOf(Props[ConnectionManager],
    "connectionManager")

  def newConnection(): Flow[Message, Message, NotUsed] = {
    val connectionActor = system.actorOf(
      Props(new Connection(connectionManager))
    )

    val incomingMessages: Sink[Message, NotUsed] =
      Flow[Message].map {
        case TextMessage.Strict(text) => Connection.IncomingMessage(text)
      }.to(Sink.actorRef[Connection.IncomingMessage]
        (connectionActor, PoisonPill))

    val outgoingMessages: Source[Message, NotUsed] =
      Source.actorRef[Connection.OutgoingMessage](10, OverflowStrategy.fail)
        .mapMaterializedValue { outActor =>
        connectionActor ! Connection.Connected(outActor)
        NotUsed
      }.map(
        (outMsg: Connection.OutgoingMessage) => TextMessage(outMsg.text)
      )

    Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
  }

  val requestHandler: HttpRequest => HttpResponse = {
    case req @ HttpRequest(GET, Uri.Path("/"), _, _, _) =>
      req.header[UpgradeToWebSocket] match {
        case Some(upgrade) => upgrade.handleMessages(newConnection())
        case None          =>
          HttpResponse(400, entity = "Not a valid websocket request!")
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

class Connection(connectionManager: ActorRef) extends Actor with ActorLogging {

  import Connection._

  override def receive: Receive = LoggingReceive {
    case Connected(outgoing) =>
      context.become(connected(outgoing))
  }

  def connected(outgoing: ActorRef): Receive = {
    connectionManager ! ConnectionManager.Connect

    {
      case IncomingMessage(text) =>
        connectionManager ! ConnectionManager.BroadcastMessage(text)

      case ConnectionManager.BroadcastMessage(text) =>
        outgoing ! OutgoingMessage(text)
    }
  }

}

object Connection {

  case class Connected(outgoing: ActorRef)
  case class IncomingMessage(text: String)
  case class OutgoingMessage(text: String)

}

class ConnectionManager extends Actor with ActorLogging {

  import ConnectionManager._

  var connections: Set[ActorRef] = Set.empty

  def receive = {

    case Connect =>
      connections += sender()
      // we also would like to remove the user when its actor is stopped
      context.watch(sender())

    case Terminated(connection) =>
      connections -= connection

    case msg: BroadcastMessage =>
      connections.foreach(_ ! msg)

  }

}

object ConnectionManager {

  case object Connect
  case class BroadcastMessage(message: String)

}
