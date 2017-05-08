package dk.diku.blockchain

import org.json4s._
import org.json4s.native.JsonMethods._
import java.text.SimpleDateFormat
import java.util.Calendar
import com.roundeights.hasher.Implicits._
import scala.language.implicitConversions

case class Loaf(data: JValue, timestamp: String, hash: String)
  (implicit validator: Validator) {

  lazy val calculateHash: String = {
   // val strippedJson = (map.toSeq.sortBy(_._1).toMap - "hash").toJson
   // strippedJson.toString.sha256
    ""
  }

  lazy val validate: Boolean = validator.loaf(this)

  lazy val toJson = JObject(
    "data" -> data,
    "timestamp" -> JString(timestamp),
    "hash" -> JString(hash)
  )
}

object Loaf {

  def generateLoaf(data: JValue)(implicit validator: Validator): Loaf = {

    val timestamp: String = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").
      format(Calendar.getInstance().getTime())

    val hash = new Loaf(data, timestamp, "").calculateHash

    new Loaf(data, timestamp, hash)
  }

  def generateLoaf(data: String)
    (implicit validator: Validator): Loaf = generateLoaf(JString(data))

  def generateLoaf(data: List[(String, String)])
    (implicit validator: Validator): Loaf = generateLoaf(JObject(data.map(
      x => JField(x._1, JString(x._2))
    )))

  /*def sortJson(json: JsValue): JsValue = {
    json match {
      case JsObject(fields) =>
        JsObject(fields.toSeq.sortBy(_._1).map({
          e: (String, JsValue) => e._1 -> sortJson(e._2)
        }).toMap)
      case e => e
    }
  }*/
}
