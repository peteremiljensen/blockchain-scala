package dk.diku.freechain

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.event.LoggingReceive

class LoafPoolActor extends Actor with ActorLogging {

  import LoafPoolActor._

  val loafPool: collection.mutable.Map[String, Loaf] =
    collection.mutable.Map()
  val minedLoafPool: collection.mutable.Map[String, Loaf] =
    collection.mutable.Map()

  override def receive: Receive = LoggingReceive {
    case AddLoaf(loaf) =>
      minedLoafPool.get(loaf.hash) match {
        case None =>
          loafPool += (loaf.hash -> loaf)
          sender() ! true
        case _ => sender() ! false
      }

    case AddLoaves(loaves) => {
      val allGood = loaves.foldLeft(true) {
        (and, l) => {
          and && (minedLoafPool.get(l.hash) match {
            case None => true
            case _ => false
          })
        }
      }
      sender() ! allGood
      if (allGood)
        loaves.foreach {l => loafPool += (l.hash -> l)}
    }

    case MineLoaf(loaf) =>
      minedLoafPool.get(loaf.hash) match {
        case None =>
          sender() ! true
          loafPool -= loaf.hash
          minedLoafPool += (loaf.hash -> loaf)
        case _ => sender() ! false
      }

    case MineLoaves(loaves) =>
      val allGood: Boolean = loaves.foldLeft(true) {
        (and, l) => {
          and && (minedLoafPool.get(l.hash) match {
            case None => true
            case _ => false
          })
        }
      }
      sender() ! allGood
      if (allGood)
        loaves.foreach {
          l => minedLoafPool += (l.hash -> l)
          loafPool -= l.hash
        }

    case ReplacePools(loafPool, minedLoafPool) =>
      sender() ! true
      this.loafPool.clear
      this.minedLoafPool.clear
      this.loafPool ++=
        loafPool.foldLeft(Map[String,Loaf]())((m,l) => m + (l.hash -> l))
      this.minedLoafPool ++=
        minedLoafPool.foldLeft(Map[String,Loaf]())((m,l) => m + (l.hash -> l))

    case GetLoaves(max) => sender() ! loafPool.take(max).values.toList
  }
}

object LoafPoolActor {
  case class AddLoaf(loaf: Loaf)
  case class AddLoaves(loaves: Seq[Loaf])
  case class MineLoaf(loaf: Loaf)
  case class MineLoaves(loaves: Seq[Loaf])
  case class ReplacePools(loafPool: Seq[Loaf], minedLoafPool: Seq[Loaf])
  case class GetLoaves(max: Integer)
}
