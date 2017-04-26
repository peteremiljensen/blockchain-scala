package models

import play.api.libs.json._
import java.text.SimpleDateFormat
import java.util.Calendar

import com.roundeights.hasher.Implicits._
import scala.language.postfixOps

case class Block(loaves: Array[Loaf], height: String,
  previous_block_hash: String, timestamp: String,
  nounce: Int, hash: String) {

  

}

object Block {
  implicit val blockFormat = Json.format[Block]
}
