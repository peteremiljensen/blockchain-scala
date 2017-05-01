package dk.diku.blockchain

import spray.json._
import DefaultJsonProtocol._
import java.text.SimpleDateFormat
import java.util.Calendar
import com.roundeights.hasher.Implicits._
import scala.language.implicitConversions

case class Loaf(data: JsValue, timestamp: String, hash: String) {

  def calculateHash: String = {
    val strippedJson = (map.toSeq.sortBy(_._1).toMap - "hash").toJson
    strippedJson.toString.sha256
  }

  def validate: Boolean = calculateHash == hash

  lazy val toJson = map.toJson

  lazy val map = Map(
    "data" -> data.toJson,
    "timestamp" -> JsString(timestamp),
    "hash" -> JsString(hash)
  )

}

object Loaf {

  def generateLoaf(data: JsValue): Loaf = {
    val timestamp: String = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").
      format(Calendar.getInstance().getTime())
    val hash = new Loaf(data, timestamp, "").calculateHash
    new Loaf(data, timestamp, hash)
  }

  def generateLoaf(data: String): Loaf = generateLoaf(JsString(data))

}
