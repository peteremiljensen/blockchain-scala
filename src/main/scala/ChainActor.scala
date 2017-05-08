package dk.diku.blockchain

import scala.collection.immutable

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import scala.language.postfixOps

class ChainActor(implicit validator: Validator) extends Actor with ActorLogging {

  import ChainActor._

  val mainChain: collection.mutable.ListBuffer[Block] =
    collection.mutable.ListBuffer()

  val loafPoolActor = context.actorSelection("/user/loafPool")
  val timeout = 20 seconds
  implicit val duration: Timeout = timeout

  override def receive: Receive = LoggingReceive {
    case AddBlock(block) =>
      if (block.validate && (mainChain.length == 0 ||
        block.previousBlockHash == mainChain.last.hash)) {

        val future = loafPoolActor ? LoafPoolActor.MineLoaves(block.loaves)
        Await.ready(future, timeout).value.get match {
          case Success(result: Boolean) if result =>
            mainChain += block; sender() ! true
          case _ => sender() ! false
        }
      } else {
        sender() ! false
      }

    case GetBlock(height) => sender() ! mainChain.lift(height)

    case ReplaceChain(chain) => {
      if (validate(chain) && chain.length > mainChain.length) {
        val indexDiffer: Int = mainChain.indexOf(mainChain.zip(chain).find {
            tuple => tuple._1.hash != tuple._2.hash
        })
        val loavesToMine = chain.map(b => b.loaves).flatten
        val loavesToAdd =
          mainChain.splitAt(indexDiffer)._2.map(b => b.loaves).flatten.
            filter(l => !loavesToMine.contains(l))
        val future =
          loafPoolActor ? LoafPoolActor.ReplacePools(loavesToAdd, loavesToMine)
        Await.ready(future, timeout).value.get match {
          case Success(result: Boolean) if result => {
            sender() ! true
            mainChain.clear
            mainChain.appendAll(chain)
          }
          case _ => sender() ! false
        }
      } else {
        sender() ! false
      }
    }

    case GetLength => sender() ! mainChain.length

    case GetChain => sender() ! mainChain.result

    case Validate => sender() ! validate(mainChain.result)

    case _ => log.info("received unknown function")
  }

  private def validate(chain: List[Block]): Boolean =
    ((1 to chain.length-1).toList.foldLeft(true) {
      (and, n) => and && chain(n).validate &&
        chain(n-1).previousBlockHash == chain(n).hash
    } && chain(0).validate)
}

object ChainActor {

  case class AddBlock(block: Block)
  case class GetBlock(height: Int)
  case class ReplaceChain(chain: List[Block])
  case object GetLength
  case object GetChain
  case object Validate
}

