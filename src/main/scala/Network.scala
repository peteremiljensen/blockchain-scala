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
import akka.util.ByteString

import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import scala.language.postfixOps

import spray.json._

class Network(port: Int)(implicit system: ActorSystem) {

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
        10, OverflowStrategy.fail)
        .mapMaterializedValue { outActor =>
        connectionActor ! ConnectionActor.Connected(outActor)
        NotUsed
      }.map(
        (outMsg: ConnectionActor.OutgoingMessage) => BinaryMessage(
          ByteString(outMsg.text))
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

  def exit = {
    import system.dispatcher
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
}

class ConnectionActor(connectionManager: ActorRef)
    extends Actor with ActorLogging {

  import ConnectionActor._

  val chainActor = context.actorSelection("/user/chain")
  val loafPoolActor = context.actorSelection("/user/loafPool")
  val timeout = 20 seconds
  implicit val duration: Timeout = timeout

  override def receive: Receive = LoggingReceive {
    case Connected(outgoing) =>
      context.become(connected(outgoing))
  }

  def connected(outgoing: ActorRef): Receive = {
    connectionManager ! ConnectionManagerActor.Connect

    {
      case IncomingMessage(text) =>
        text.parseJson.asJsObject.getFields("type", "function") match {
          case Seq(JsString("request"), JsString("get_length")) =>
            Await.ready(chainActor ? ChainActor.GetLength,
              timeout).value.get match {
              case Success(length: Integer) =>
                outgoing ! OutgoingMessage(JsObject(
                  "type" -> JsString("response"),
                  "function" -> JsString("get_length"),
                  "length" -> JsNumber(length)
                ).toString)
              case _ =>
            }

          case Seq(JsString("request"), JsString("")) =>

          case _ => log.warning("*** incoming message is invalid: " + text)
        }
        //connectionManager ! ConnectionManager.BroadcastMessage(text)

      case ConnectionManagerActor.BroadcastMessage(text) =>
        outgoing ! OutgoingMessage(text)
    }
  }
}

object ConnectionActor {
  case class Connected(outgoing: ActorRef)
  case class IncomingMessage(text: String)
  case class OutgoingMessage(text: String)
}

class ConnectionManagerActor extends Actor with ActorLogging {

  import ConnectionManagerActor._

  var connections: Set[ActorRef] = Set.empty

  def receive = {

    case Connect =>
      connections += sender()
      context.watch(sender())

    case Terminated(connection) =>
      connections -= connection

    case msg: BroadcastMessage =>
      connections.foreach(_ ! msg)
  }
}

object ConnectionManagerActor {
  case object Connect
  case class BroadcastMessage(message: String)
}
