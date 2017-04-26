package models

import play.api.libs.json._
import java.text.SimpleDateFormat
import java.util.Calendar

import com.roundeights.hasher.Implicits._
import scala.language.postfixOps

case class Loaf(data: JsValue, timestamp: String, hash: String)

object Loaf {
  implicit val loafFormat = Json.format[Loaf]

  def generateLoaf(data: JsValue) = {
    val timestamp: String = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").
      format(Calendar.getInstance().getTime())
    val stripped_json = Json.toJson(Loaf(data, timestamp, "")).as[JsObject] - "hash"
    val sorted_json = JsObject(stripped_json.fields.sortBy(_._1))
    println(sorted_json.toString)
    val hash = sorted_json.toString.sha256
    Loaf(
      data,
      timestamp,
      hash
    )
  }
}
