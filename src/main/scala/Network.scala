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
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model.HttpMethods._
import akka.NotUsed
import akka.util.ByteString

import org.json4s._
import org.json4s.native.JsonMethods._

class Network(port: Int)(implicit system: ActorSystem, validator: Validator) {

  implicit val materializer = ActorMaterializer()

  private val connectionManager = system.actorOf(Props[ConnectionManagerActor],
    "connectionManager")

  private def newConnection(): Flow[Message, Message, NotUsed] = {
    val connectionActor = system.actorOf(
      Props(new ConnectionActor(connectionManager)))

    val incomingMessages: Sink[Message, NotUsed] =
      Flow[Message].map {
        case BinaryMessage.Strict(msg) =>
          ConnectionActor.IncomingMessage(msg.decodeString("UTF-8"))
        case _ => ConnectionActor.IncomingMessage("")
      }.to(Sink.actorRef[ConnectionActor.IncomingMessage]
        (connectionActor, PoisonPill))

    val outgoingMessages: Source[Message, NotUsed] =
      Source.actorRef[ConnectionActor.OutgoingMessage](
        10000, OverflowStrategy.dropNew)
        .mapMaterializedValue { outActor =>
        connectionActor ! ConnectionActor.Connected(outActor)
        NotUsed
      }.map(
        (outMsg: ConnectionActor.OutgoingMessage) => BinaryMessage(
          ByteString(compact(render(outMsg.json))))
      )

    Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
  }

  private val requestHandler: HttpRequest => HttpResponse = {
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

  private val bindingFuture =
    Http().bindAndHandleSync(requestHandler, interface = "0.0.0.0", port = port)

  def connect(ip: String, port: Integer) =
    Http().singleWebSocketRequest(WebSocketRequest(
      "ws://"+ip+":"+port.toString),
      newConnection())

  def exit = {
    import system.dispatcher
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }

  def broadcastLoaf(loaf: Loaf) = {
    connectionManager ! ConnectionManagerActor.BroadcastMessage(JObject(
      "type" -> JString("request"),
      "function" -> JString("broadcast_loaf"),
      "loaf" -> loaf.toJson
    ))
  }

  def broadcastBlock(block: Block) = {
    connectionManager ! ConnectionManagerActor.BroadcastMessage(JObject(
      "type" -> JString("request"),
      "function" -> JString("broadcast_block"),
      "block" -> block.toJson
    ))
  }
}
