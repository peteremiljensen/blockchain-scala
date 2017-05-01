package dk.diku.blockchain

import spray.json._
import DefaultJsonProtocol._
import java.text.SimpleDateFormat
import java.util.Calendar
import com.roundeights.hasher.Implicits._
import scala.language.implicitConversions

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
