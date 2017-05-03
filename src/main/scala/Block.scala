package dk.diku.blockchain

import spray.json._
import DefaultJsonProtocol._
import java.text.SimpleDateFormat
import java.util.Calendar
import com.roundeights.hasher.Implicits._


case class Block(loaves: Seq[Loaf], height: Int,
  previousBlockHash: String, timestamp: String,
  data: JsValue, hash: String)(implicit validator: Validator) {

  def calculateHash: String = {
    val strippedJson = (map.toSeq.sortBy(_._1).toMap - "hash").toJson
    strippedJson.toString.sha256
  }

  def validate: Boolean = calculateHash == hash && validator.block(this)

  lazy val toJson = map.toJson

  lazy val map = Map(
    "loaves" -> loaves.map(l => l.toJson).toJson,
    "height" -> JsNumber(height),
    "previous_block_hash" -> JsString(previousBlockHash),
    "data" -> data.toJson,
    "hash" -> JsString(hash)
  )
}
