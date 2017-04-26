package models

import play.api.libs.json._
import java.text.SimpleDateFormat
import java.util.Calendar

import com.roundeights.hasher.Implicits._
import scala.language.postfixOps

case class Block(loaves: Array[Loaf], height: String, previous_block_hash: String,
				timestamp: String, nounce: Int, hash: String)

object Block {
	implicit val blockFormat = Json.format[Block]

	def generateBlock(loaves: Array[Loaf], height: String, previous_block_hash: String, nounce: Int) = {
		val timestamp: String = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").
			format(Calendar.getInstance().getTime())
		val stripped_json = Json.toJson(Block(loaves, height, previous_block_hash, timestamp, nounce, "")).as[JsObject] - "hash"
		val sorted_json = JsObject(stripped_json.fields.sortBy(_._1))
		println(sorted_json.toString)
		val hash = sorted_json.toString.sha256
		Block(
			loaves,
			height,
			previous_block_hash,
			timestamp,
			nounce,
			hash
		)
	}
}