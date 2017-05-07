package dk.diku.blockchain

import akka.actor._
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import scala.language.postfixOps

import spray.json._

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
    outgoing ! OutgoingMessage(JsObject(
      "type" -> JsString("request"),
      "function" -> JsString("get_length")
    ).toString)

    {
      case IncomingMessage(text) =>
        try {
          val json: JsObject = text.parseJson.asJsObject
          json.getFields("type", "function") match {
            case Seq(JsString(typeStr), JsString("get_length")) =>
              handleGetLength(json, outgoing, typeStr)

            case Seq(JsString("request"), JsString("broadcast_loaf")) =>
              handleBroadcastLoaf(json, outgoing, text)

            case _ => log.warning("*** incoming message is invalid: " + text)
          }
        } catch {
          case e: JsonParser.ParsingException =>
            log.error("*** json parsing error: " + e)
        }

      case ConnectionManagerActor.BroadcastMessage(text) =>
        outgoing ! OutgoingMessage(text)
    }

  }

  def handleGetLength(json: JsObject, outgoing: ActorRef, typeStr: String) =
    typeStr match {
      case "request" => Await.ready(chainActor ? ChainActor.GetLength,
        timeout).value.get match {
        case Success(length: Integer) =>
          outgoing ! OutgoingMessage(JsObject(
            "type" -> JsString("response"),
            "function" -> JsString("get_length"),
            "length" -> JsNumber(length)
          ).toString)
        case _ => log.warning("*** invalid ChainActor response")
      }

      case "response" => json.getFields("length") match {
        case Seq(JsNumber(recLength)) =>
          Await.ready(chainActor ? ChainActor.GetLength,
            timeout).value.get match {
            case Success(localLength: Integer) =>
              if (validator.consensusCheck(localLength, recLength.toInt)) {
                outgoing ! OutgoingMessage(JsObject(
                  "type" -> JsString("request"),
                  "function" -> JsString("get_chain")
                ).toString)
              }
            case _ => log.warning("*** invalid ChainActor response")
          }

        case _ => log.warning("*** invalid get_length response")
      }

      case _ =>  log.warning("*** get_length type is invalid")
    }

  def handleBroadcastLoaf(json: JsObject, outgoing: ActorRef, text: String) =
    json.getFields("loaf") match {
      case Seq(loaf) =>
        loaf.asJsObject.getFields("data", "timestamp", "hash") match {
          case Seq(data, JsString(timestamp), JsString(hash)) =>
            val l = new Loaf(data, timestamp, hash)
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
}

object ConnectionActor {
  case class Connected(outgoing: ActorRef)
  case class IncomingMessage(text: String)
  case class OutgoingMessage(text: String)
}
