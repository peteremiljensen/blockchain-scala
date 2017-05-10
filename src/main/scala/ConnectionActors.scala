package dk.diku.blockchain

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
  val timeout = 20 seconds
  implicit val duration: Timeout = timeout

  override def receive: Receive = LoggingReceive {
    case Connected(outgoing) =>
      context.become(connected(outgoing))
  }

  val getLengthJson = JObject(
    "type" -> JString("request"),
    "function" -> JString("get_length")
  )

  def connected(outgoing: ActorRef): Receive = {
    connectionManager ! ConnectionManagerActor.Connect
    outgoing ! OutgoingMessage(getLengthJson)

    {
      case IncomingMessage(text) =>
        try {
          val json = parse(text)
            (json \ "type", json \ ("function")) match {
            case (JString(typeStr), JString("get_length")) =>
              handleGetLength(json, outgoing, typeStr)

            case (JString(typeStr), JString("get_chain")) =>
              handleGetChain(json, outgoing, typeStr)

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

  private def handleGetLength(json: JValue, outgoing: ActorRef, typeStr: String) =
    typeStr match {
      case "request" => Await.ready(chainActor ? ChainActor.GetLength,
        timeout).value.get match {
        case Success(length: Integer) =>
          outgoing ! OutgoingMessage(JObject(
            "type" -> JString("response"),
            "function" -> JString("get_length"),
            "length" -> JInt(BigInt(length))
          ))
        case _ => log.warning("*** invalid ChainActor response")
      }

      case "response" => json \ "length" match {
        case JInt(recLength) =>
          Await.ready(chainActor ? ChainActor.GetLength,
            timeout).value.get match {
            case Success(localLength: Integer) =>
              if (validator.consensusCheck(localLength, recLength.toInt)) {
                outgoing ! OutgoingMessage(JObject(
                  "type" -> JString("request"),
                  "function" -> JString("get_chain")
                ))
              }
            case _ => log.warning("*** invalid ChainActor response")
          }

        case _ => log.warning("*** invalid get_length response")
      }

      case _ =>  log.warning("*** get_length type is invalid")
    }

  private def handleGetChain(json: JValue, outgoing: ActorRef, typeStr: String) =
    typeStr match {
      case "request" => Await.ready(chainActor ? ChainActor.GetChain,
        timeout).value.get match {
        case Success(chain: List[Block] @unchecked) =>
          outgoing ! OutgoingMessage(JObject(
            "type" -> JString("response"),
            "function" -> JString("get_chain"),
            "chain" -> JArray(chain.map(_.toJson))
          ))
        case _ => log.warning("*** could not receive chain from actor")
      }
      case "response" => (json \ "chain") match {
        case JArray(chainJson) =>
          val chain = chainJson.map(jsonToBlock)
          if (!chain.contains(None))
            Await.ready(chainActor ? ChainActor.ReplaceChain(chain.map(_.get)),
              timeout).value.get match {
              case Success(true) => log.info("*** chain succesfully replaced")
              case Success(false) => log.info("*** chain could not be replaced")
              case _ => log.warning("*** chainActor returned failure")
            }
          else
            log.warning("*** received invalid chain")
        case _ => log.warning("*** no chain is received")
      }
      case _ => log.warning("*** get_chain type is invalid")
    }

  private def handleBroadcastLoaf(json: JValue, outgoing: ActorRef) =
    jsonToLoaf(json \ "loaf") match {
      case Some(loaf) =>
        if (loaf.validate) {
          Await.ready(loafPoolActor ? LoafPoolActor.AddLoaf(loaf),
            timeout).value.get match {
            case Success(result: Boolean) if result =>
              log.info("*** loaf added")
              connectionManager ! ConnectionManagerActor.
                BroadcastMessage(json)
            case _ =>
          }
        }
      case _ => log.warning("*** incoming loaf is invalid")
    }

  private def handleBroadcastBlock(json: JValue, outgoing: ActorRef) =
    jsonToBlock(json \ "block") match {
      case Some(block) =>
        Await.ready(chainActor ? ChainActor.GetLength,
          timeout).value.get match {
          case Success(length: Int) =>
            if (block.height <= length) {
              Await.ready(chainActor ? ChainActor.AddBlock(block),
                timeout).value.get match {
                case Success(true) =>
                  log.info("*** block added")
                  connectionManager ! ConnectionManagerActor.
                    BroadcastMessage(json)
                case Success(false) =>
                case _ => log.error("*** could not contact ChainActor")
              }
            } else {
              outgoing ! OutgoingMessage(getLengthJson)
            }
          case _ => log.error("*** could not contact ChainActor")
        }
      case _ => log.warning("*** received invalid block")
    }

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
