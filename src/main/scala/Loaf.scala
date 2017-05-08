package dk.diku.blockchain

import spray.json._
import DefaultJsonProtocol._
import java.text.SimpleDateFormat
import java.util.Calendar
import com.roundeights.hasher.Implicits._
import scala.language.implicitConversions

case class Loaf(data: JsValue, timestamp: String, hash: String)
  (implicit validator: Validator) {

  lazy val calculateHash: String = {
    val strippedJson = (map.toSeq.sortBy(_._1).toMap - "hash").toJson
    strippedJson.toString.sha256
  }

  lazy val validate: Boolean = validator.loaf(this)

  lazy val toJson = map.toJson

  lazy val map = Map(
    "data" -> Loaf.sortJson(data),
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

  def sortJson(json: JsValue): JsValue = {
    json match {
      case JsObject(fields) =>
        JsObject(fields.toSeq.sortBy(_._1).map({
          e: (String, JsValue) => e._1 -> sortJson(e._2)
        }).toMap)
      case e => e
    }
  }
}
