package models

import spray.json._
import DefaultJsonProtocol._
import java.text.SimpleDateFormat
import java.util.Calendar

import com.roundeights.hasher.Implicits._
import scala.language.postfixOps

case class Loaf(data: JsValue, timestamp: String, hash: String) {

  def calculateHash: String = {
    val stripped_json = Json.toJson(this).as[JsObject] - "hash"
    val sorted_json = JsObject(stripped_json.fields.sortBy(_._1))
    sorted_json.toString.sha256
  }

  def validate: Boolean = calculateHash == hash

}

object Loaf {

  implicit val loafFormat: Writes[Loaf] = (
    (JsPath \ "data").write[JsValue] and
    (JsPath \ "timestamp").write[String] and
    (JsPath \ "hash").write[String]
  )(unlift(Loaf.unapply))

  def generateLoaf(data: JsValue): Loaf = {
    val timestamp: String = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").
      format(Calendar.getInstance().getTime())
    val hash = Loaf(data, timestamp, "").calculateHash
    Loaf(data, timestamp, hash)
  }

}
