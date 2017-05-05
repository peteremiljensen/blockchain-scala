package dk.diku.blockchain

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.event.LoggingReceive

class LoafPoolActor extends Actor with ActorLogging {

  import LoafPoolActor._

  val loafPool: collection.mutable.Map[String, Loaf] =
    collection.mutable.Map()
  val minedLoavesPool: collection.mutable.Map[String, Loaf] =
    collection.mutable.Map()

  override def receive: Receive = LoggingReceive {
    case AddLoaf(loaf) =>
      (minedLoavesPool.get(loaf.hash), loafPool.get(loaf.hash)) match {
        case (None, None) =>
          loafPool += (loaf.hash -> loaf)
          sender() ! true
        case _ => sender() ! false
      }

    case MineLoaf(hash) =>
      loafPool.get(hash) match {
        case Some(loaf) =>
          minedLoavesPool += (hash -> loaf)
          loafPool -= hash
          sender() ! true
        case _ => sender() ! false
      }

    case GetLoaves(max) => sender() ! Seq(loafPool.take(max).values)
  }
}

object LoafPoolActor {
  case class AddLoaf(loaf: Loaf)
  case class MineLoaf(hash: String)
  case class GetLoaves(max: Integer)
}
