package dk.diku.blockchain

import scala.collection.immutable

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.event.LoggingReceive
import spray.json._

class ChainActor(implicit validator: Validator) extends Actor with ActorLogging {

  import ChainActor._

  val chain: collection.mutable.ListBuffer[Block] =
    collection.mutable.ListBuffer(Block(
      Seq(), 0, "-1", "2017-04-24 17:17:44.226598", JsObject(),
      "00000ac00538be65f659795fb9a021adf05c2c36f1ebd7c2c0249622edfccee6"
    ))

  override def receive: Receive = LoggingReceive {

    case AddBlock(block) =>
      if (block.validate && block.previousBlockHash == chain.last.hash) {
        chain += block
        sender() ! true
      } else {
        sender() ! false
      }

    case GetBlock(height) => sender() ! chain.lift(height)

    case GetLength => sender() ! chain.length

    case GetChain => sender() ! chain.result

    case Validate => sender() !
      ((1 to chain.length-1).toList.foldLeft(true) (
        (and, n) => and && chain(n).validate &&
          chain(n-1).previousBlockHash == chain(n).hash
      ) && chain(0).validate)

    case _ => log.info("received unknown function")

  }

}

object ChainActor {

  case class AddBlock(block: Block)
  case class GetBlock(height: Int)
  case object GetLength
  case object GetChain
  case object Validate

}

