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
    val sorted_json = Json.toJson(
      Map("data" -> "test", "timestamp" -> timestamp)
    ).as[JsObject]
    val hash = "test"
    Loaf(
      data,
      timestamp,
      hash
    )
  }
}
