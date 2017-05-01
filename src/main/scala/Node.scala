package dk.diku.blockchain

import akka.actor.{ActorSystem, Props}
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import scala.language.postfixOps

class Node(port: Int) {

  val system = ActorSystem("bc-system")
  val chainActor = system.actorOf(Props[ChainActor], "chain")
  val timeout = 20 seconds
  implicit val duration: Timeout = timeout

  def addBlock(block: Block) = askWait(AddBlock(block))
  def getBlock(height: Int) = askWait(GetBlock(height))
  def getLength = askWait(GetLength)
  def getChain = askWait(GetChain)
  def validate = askWait(Validate)

  def askWait(message: Any) = {
    val f = chainActor ? message
    val result = Await.ready(f, timeout).value.get
    result match {
      case Success(data) => Right(data)
      case Failure(data) => Left(data)
    }
  }

}
