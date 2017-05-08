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
    val strippedJson = compact(render(Loaf.sortJson(JObject(
      toJson.obj.filter(_._1 != "hash")
    ))))
    strippedJson.toString.sha256
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

  def sortJson(json: JValue): JValue = {
    json match {
      case JObject(fields) =>
        JObject(fields.sortBy(_._1).map({
          e: (String, JValue) => e._1 -> sortJson(e._2)
        }))
      case JArray(e) => JArray(e.map(sortJson))
      case e => e
    }
  }
}
