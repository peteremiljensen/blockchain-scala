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

    case AddLoaves(loaves) => {
      val allGood = loaves.foldLeft(true) {
        (and, l) => {
          and && ((minedLoavesPool.get(l.hash), loafPool.get(l.hash)) match {
            case (None, None) => true
            case _ => false
          })
        }
      }
      sender() ! allGood
      if (allGood)
        loaves.foreach {l => loafPool += (l.hash -> l)}
    }

    case MineLoaf(hash) =>
      loafPool.get(hash) match {
        case Some(loaf) =>
          minedLoavesPool += (hash -> loaf)
          loafPool -= hash
          sender() ! true
        case _ => sender() ! false
      }

    case MineLoaves(hashes) =>
      val allGood: Boolean = hashes.foldLeft(true) {
        (and, h) => {
          and && (loafPool.get(h) match {
            case Some(_) => true
            case _ => false
          })
        }
      }
      sender() ! allGood
      if (allGood)
        hashes.foreach {
          h => minedLoavesPool += (h -> loafPool.get(h).get)
          loafPool -= h
        }

    case GetLoaves(max) => sender() ! Seq(loafPool.take(max).values)
  }
}

object LoafPoolActor {
  case class AddLoaf(loaf: Loaf)
  case class AddLoaves(loaves: Seq[Loaf])
  case class MineLoaf(hash: String)
  case class MineLoaves(hashes: Seq[String])
  case class GetLoaves(max: Integer)
}
