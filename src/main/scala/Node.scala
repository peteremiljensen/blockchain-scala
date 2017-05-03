package dk.diku.blockchain

import akka.actor.{ActorSystem, Props}
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import scala.language.postfixOps

class Node(port: Int)
  (implicit system: ActorSystem = ActorSystem(), validator: Validator) {

  val network: Network = new Network(port)

  val chainActor = system.actorOf(Props(new ChainActor), "chain")
  val loafPoolActor = system.actorOf(Props(new LoafPoolActor), "loafPool")
  val timeout = 20 seconds
  implicit val duration: Timeout = timeout

  def addBlock(block: Block) = askWait(ChainActor.AddBlock(block))
  def getBlock(height: Int) = askWait(ChainActor.GetBlock(height))
  def getLength = askWait(ChainActor.GetLength)
  def getChain = askWait(ChainActor.GetChain)
  def validate = askWait(ChainActor.Validate)

  def askWait(message: Any) = {
    val result = Await.ready(chainActor ? message, timeout).value.get
    result match {
      case Success(data) => Right(data)
      case Failure(data) => Left(data)
    }
  }
}
