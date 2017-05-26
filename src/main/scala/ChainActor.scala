package dk.diku.freechain

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
          case Success(true) =>
            mainChain += block; sender() ! true
          case _ => sender() ! false
        }
      } else {
        sender() ! false
      }

    case GetBlock(height) => sender() ! mainChain.lift(height)

    case GetBlocks(offset, length) => sender() !
      mainChain.result.slice(offset, length + offset)

    case Branching(blocks) if blocks.length > 0 => {
      val remoteChain = mainChain.result.splitAt(blocks(0).height)._1 ::: blocks
      if (validate(remoteChain)) {
        if (blocks(0).height == mainChain.length) {
          // Directly appendable
          if (replaceChain(remoteChain))
            sender ! Some(remoteChain.last)
          else
            sender ! None
        } else {
          // Branching has to be consulted
          if (replaceChain(
            validator.branching(mainChain.result, remoteChain)
          ))
            sender ! Some(remoteChain.last)
          else
            sender ! None
        }
      }
    }

    case GetChain => sender() ! mainChain.result

    case GetHashes => sender() ! mainChain.result.map(_.hash)

    case GetLength => sender() ! mainChain.length

    case Validate => sender() ! validate(mainChain.result)

    case _ => log.info("received unknown function")
  }

  def replaceChain(chain: List[Block]): Boolean = {
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
      case Success(true) => {
        mainChain.clear
        mainChain.appendAll(chain)
        true
      }
      case _ => false
    }
  }
}

object ChainActor {

  case class AddBlock(block: Block)
  case class GetBlock(height: Int)
  case class GetBlocks(offset: Int, length: Int)
  case class Branching(blocks: List[Block])
  case object GetChain
  case object GetHashes
  case object GetLength
  case object Validate

  def validate(chain: List[Block]): Boolean =
    ((1 to chain.length-1).toList.foldLeft(true) {
      (and, n) => and && chain(n).validate &&
        chain(n).previousBlockHash == chain(n-1).hash
    }) && chain.length > 0 && chain(0).validate && chain(0).height == 0
}

