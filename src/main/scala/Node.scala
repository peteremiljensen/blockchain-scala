package dk.diku.blockchain

import akka.actor.{ActorSystem, Props, ActorRef}
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import scala.language.postfixOps

class Node(port: Int)
  (implicit system: ActorSystem = ActorSystem(), validator: Validator) {

  private val network: Network = new Network(port)

  private val chainActor = system.actorOf(Props(new ChainActor), "chain")
  private val loafPoolActor = system.actorOf(Props(new LoafPoolActor), "loafPool")
  private val timeout = 20 seconds
  private implicit val duration: Timeout = timeout

  def addBlock(block: Block) = askWait(chainActor, ChainActor.AddBlock(block))
  def getBlock(height: Int) = askWait(chainActor, ChainActor.GetBlock(height))
  def getLength = askWait(chainActor, ChainActor.GetLength)
  def getChain = askWait(chainActor, ChainActor.GetChain)
  def validate = askWait(chainActor, ChainActor.Validate)

  def addLoaf(loaf: Loaf) = askWait(loafPoolActor,
    LoafPoolActor.AddLoaf(loaf)) match {
    case Right(result: Boolean) => network.broadcastLoaf(loaf); result
    case _ => false
  }

  private def askWait(actor: ActorRef, message: Any) = {
    val result = Await.ready(actor ? message, timeout).value.get
    result match {
      case Success(data) => Right(data)
      case Failure(data) => Left(data)
    }
  }
}
