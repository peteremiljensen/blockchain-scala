import scala.collection.mutable

class Loaf(data: String, timestamp: String = "None", hash: String = "None") {

	val loaf = scala.collection.mutable.Map("data" -> data, "timestamp" -> timestamp, "hash" -> hash);

	if (loaf("timestamp") == "None") loaf.update("timestamp", DateTime.now.toString)
}