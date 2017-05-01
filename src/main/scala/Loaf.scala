package dk.diku.blockchain

import spray.json._
import DefaultJsonProtocol._
import java.text.SimpleDateFormat
import java.util.Calendar
import com.roundeights.hasher.Implicits._
import scala.language.implicitConversions

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.event.LoggingReceive

case class Loaf(data: JsValue, timestamp: String, hash: String)
  (implicit validator: Validator) {

  def calculateHash: String = {
    val strippedJson = (map.toSeq.sortBy(_._1).toMap - "hash").toJson
    strippedJson.toString.sha256
  }

  def validate: Boolean = calculateHash == hash && validator.loaf(this)

  lazy val toJson = map.toJson

  lazy val map = Map(
    "data" -> data.toJson,
    "timestamp" -> JsString(timestamp),
    "hash" -> JsString(hash)
  )

}

object Loaf {

  def generateLoaf(data: JsValue)(implicit validator: Validator): Loaf = {
    val timestamp: String = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").
      format(Calendar.getInstance().getTime())
    val hash = new Loaf(data, timestamp, "").calculateHash
    new Loaf(data, timestamp, hash)
  }

  def generateLoaf(data: String)
    (implicit validator: Validator): Loaf = generateLoaf(JsString(data))

  def generateLoaf(data: Map[String, String])
    (implicit validator: Validator): Loaf = generateLoaf(data.toJson)

}

class LoafPoolActor extends Actor with ActorLogging {

  import LoafPoolActor._

  val loafPool: collection.mutable.Map[String, Loaf] =
    collection.mutable.Map()
  val minedLoavesPool: collection.mutable.Map[String, Loaf] =
    collection.mutable.Map()

  override def receive: Receive = LoggingReceive {
    case AddLoaf(loaf) =>
      (minedLoavesPool.get(loaf.hash), loafPool.get(loaf.hash)) match {
        case (None, None) =>
          loafPool += (loaf.hash -> loaf)
          sender() ! true
        case _ => sender() ! false
      }

    case MineLoaf(hash) =>
      loafPool.get(hash) match {
        case Some(loaf) =>
          minedLoavesPool += (hash -> loaf)
          loafPool -= hash
          sender() ! true
        case _ => sender() ! false
      }

    case GetLoaves(max) => sender() ! Seq(loafPool.take(max).values)

  }

}

object LoafPoolActor {

  case class AddLoaf(loaf: Loaf)
  case class MineLoaf(hash: String)
  case class GetLoaves(max: Integer)

}
