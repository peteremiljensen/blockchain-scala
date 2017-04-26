package models

import play.api.libs.json._
import play.api.libs.functional.syntax._
import java.text.SimpleDateFormat
import java.util.Calendar

import com.roundeights.hasher.Implicits._
import scala.language.postfixOps

case class Block(loaves: Seq[Loaf], height: Int,
  previous_block_hash: String, timestamp: String,
  nounce: Int, hash: String) {

  def calculateHash: String = {
    val stripped_json = Json.toJson(this).as[JsObject] - "hash"
    val sorted_json = JsObject(stripped_json.fields.sortBy(_._1))
    sorted_json.toString.sha256
  }

}

object Block {
  implicit val blockFormat: Writes[Block] = (
    (JsPath \ "loaves").write[Seq[Loaf]] and
    (JsPath \ "height").write[Int] and
    (JsPath \ "previous_block_hash").write[String] and
    (JsPath \ "timestamp").write[String] and
    (JsPath \ "nounce").write[Int] and
    (JsPath \ "hash").write[String]
  )(unlift(Block.unapply))
}
