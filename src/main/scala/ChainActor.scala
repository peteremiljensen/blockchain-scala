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
import spray.json._

class ChainActor(implicit validator: Validator) extends Actor with ActorLogging {

  import ChainActor._

  val mainChain: collection.mutable.ListBuffer[Block] =
    collection.mutable.ListBuffer(Block(
      Seq(), 0, "-1", "2017-04-24 17:17:44.226598", JsObject(),
      "00000ac00538be65f659795fb9a021adf05c2c36f1ebd7c2c0249622edfccee6"
    ))

  val loafPoolActor = context.actorSelection("/user/loafPool")
  val timeout = 20 seconds
  implicit val duration: Timeout = timeout

  override def receive: Receive = LoggingReceive {
    case AddBlock(block) =>
      if (block.validate && block.previousBlockHash == mainChain.last.hash) {
        val future = loafPoolActor ? LoafPoolActor.MineLoaves(
          block.loaves.map(l => l.hash)
        )
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
        val loavesToAdd =
          mainChain.splitAt(indexDiffer)._2.map(b => b.loaves).flatten
        val loavesToMine = chain.map(b => b.loaves).flatten
        val future =
          loafPoolActor ? LoafPoolActor.ReplacePools(loavesToAdd, loavesToMine)
        // TODO: NOT FINISHED AS THERE CAN BE OVERLAPS BETWEEN THE TWO POOLS
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

