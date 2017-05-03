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

import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import scala.language.postfixOps

import spray.json._

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
    connectionManager ! ConnectionManagerActor.BroadcastMessage(JsObject(
      "type" -> JsString("request"),
      "function" -> JsString("broadcast_loaf"),
      "loaf" -> loaf.toJson
    ).toString)
  }
}

class ConnectionActor(connectionManager: ActorRef)
  (implicit validator: Validator)
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
        try {
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
                case _ => log.warning("*** cannot do get_length")
              }

            case Seq(JsString("response"), JsString("get_length")) =>

            case Seq(JsString("request"), JsString("broadcast_loaf")) =>
              text.parseJson.asJsObject.getFields("loaf") match {
                case Seq(loaf) =>
                  loaf.asJsObject.getFields("data", "timestamp", "hash") match {
                    case Seq(data, JsString(timestamp), JsString(hash)) =>
                      val l: Loaf = new Loaf(data, timestamp, hash)
                      if (l.validate) {
                        Await.ready(loafPoolActor ? LoafPoolActor.AddLoaf(l),
                          timeout).value.get match {
                          case Success(result: Boolean) if result =>
                            log.info("*** loaf added")
                            connectionManager ! ConnectionManagerActor.
                              BroadcastMessage(text)
                          case _ =>
                        }
                      }
                    case _ => log.warning("*** incoming loaf is invalid")
                  }
                case _ => log.warning("*** incoming loaf is invalid")
              }

            case _ => log.warning("*** incoming message is invalid: " + text)
          }
        } catch {
          case e: JsonParser.ParsingException =>
            log.error("*** json parsing error")
        }

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

    case BroadcastMessage(msg) =>
      connections.foreach(_ ! BroadcastMessage(msg))
  }
}

object ConnectionManagerActor {
  case object Connect
  case class BroadcastMessage(message: String)
}
