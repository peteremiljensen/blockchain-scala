package dk.diku.freechain

import akka.actor._
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import scala.language.postfixOps

import org.json4s._
import org.json4s.native.JsonMethods._

class ConnectionManagerActor extends Actor with ActorLogging {

  import ConnectionManagerActor._

  var connections: Set[ActorRef] = Set.empty

  def receive = {

    case Connect =>
      connections += sender()
      context.watch(sender())

    case Terminated(connection) =>
      connections -= connection

    case BroadcastMessage(json) =>
      connections.foreach(_ ! BroadcastMessage(json))
  }
}

object ConnectionManagerActor {
  case object Connect
  case class BroadcastMessage(json: JValue)
}



class ConnectionActor(connectionManager: ActorRef)
  (implicit validator: Validator)
    extends Actor with ActorLogging {

  import ConnectionActor._

  val chainActor = context.actorSelection("/user/chain")
  val loafPoolActor = context.actorSelection("/user/loafPool")
  val eventsActor = context.actorSelection("/user/events")
  val timeout = 20 seconds
  implicit val duration: Timeout = timeout

  override def receive: Receive = LoggingReceive {
    case Connected(outgoing) =>
      context.become(connected(outgoing))
  }

  val getHashesJson = JObject(
    "type" -> JString("request"),
    "function" -> JString("get_hashes")
  )

  def connected(outgoing: ActorRef): Receive = {
    connectionManager ! ConnectionManagerActor.Connect
    outgoing ! OutgoingMessage(getHashesJson)
    eventsActor ! Events.ConnectionReady

    {
      case IncomingMessage(text) =>
        try {
          val json = parse(text)
            (json \ "type", json \ ("function")) match {
            case (JString(typeStr), JString("get_hashes")) =>
              handleGetHashes(json, outgoing, typeStr)

            case (JString(typeStr), JString("get_blocks")) =>
              handleGetBlocks(json, outgoing, typeStr)

            case (JString("request"), JString("broadcast_loaf")) =>
              handleBroadcastLoaf(json, outgoing)

            case (JString("request"), JString("broadcast_block")) =>
              handleBroadcastBlock(json, outgoing)

            case _ => log.warning("*** incoming message is invalid: " + text)
          }
        } catch {
          case e: ParserUtil.ParseException =>
            log.error("*** json parsing error: " + e)
        }

      case ConnectionManagerActor.BroadcastMessage(json) =>
        outgoing ! OutgoingMessage(json)
    }

  }

  private def handleGetHashes(json: JValue, outgoing: ActorRef, typeStr: String) =
    typeStr match {
      case "request" => Await.ready(chainActor ? ChainActor.GetHashes,
        timeout).value.get match {
        case Success(hashes: List[String] @unchecked) =>
          outgoing ! OutgoingMessage(JObject(
            "type" -> JString("response"),
            "function" -> JString("get_hashes"),
            "hashes" -> JArray(hashes.map(JString(_)))
          ))
        case _ => log.warning("*** invalid ChainActor response")
      }

      case "response" =>

      case _ =>  log.warning("*** get_hashes type is invalid")
    }

  private def handleGetBlocks(json: JValue, outgoing: ActorRef, typeStr: String) =
    Unit

  private def handleBroadcastLoaf(json: JValue, outgoing: ActorRef) =
    jsonToLoaf(json \ "loaf") match {
      case Some(loaf) =>
        if (loaf.validate) {
          Await.ready(loafPoolActor ? LoafPoolActor.AddLoaf(loaf),
            timeout).value.get match {
            case Success(true) =>
              log.info("*** loaf added")
              connectionManager ! ConnectionManagerActor.
                BroadcastMessage(json)
            case _ =>
          }
        }
      case _ => log.warning("*** incoming loaf is invalid")
    }

  private def handleBroadcastBlock(json: JValue, outgoing: ActorRef) =
    Unit

  private def jsonToLoaf(json: JValue) =
    (json \ "data",
      json \ "timestamp",
      json \ "hash") match {
      case (data, JString(timestamp), JString(hash)) =>
        Some(Loaf(data, timestamp, hash))
      case _ => None
    }

  private def jsonToBlock(json: JValue): Option[Block] =
    (json \ "loaves",
      json \ "height",
      json \ "previous_block_hash",
      json \ "timestamp",
      json \ "data",
      json \ "hash") match {
      case (JArray(loaves), JInt(height), JString(previousBlockHash),
        JString(timestamp), data, JString(hash)) =>
        val ls: Seq[Option[Loaf]] = loaves.map(jsonToLoaf).toSeq
        if (!ls.contains(None) && height.isValidInt)
          Some(Block(ls.map(_.get), height.toInt, previousBlockHash,
            timestamp, data, hash))
        else
          None
      case _ => None
    }

}

object ConnectionActor {
  case class Connected(outgoing: ActorRef)
  case class IncomingMessage(text: String)
  case class OutgoingMessage(json: JValue)
}
