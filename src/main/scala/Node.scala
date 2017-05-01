package dk.diku.blockchain

import akka.actor.{ActorSystem, Props}
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import scala.language.postfixOps

class Node(port: Int)(implicit system: ActorSystem) {

  val network: Network = new Network(port)

  val chainActor = system.actorOf(Props[ChainActor], "chain")
  val timeout = 20 seconds
  implicit val duration: Timeout = timeout

  def addBlock(block: Block) = askWait(ChainActor.AddBlock(block))
  def getBlock(height: Int) = askWait(ChainActor.GetBlock(height))
  def getLength = askWait(ChainActor.GetLength)
  def getChain = askWait(ChainActor.GetChain)
  def validate = askWait(ChainActor.Validate)

  def askWait(message: Any) = {
    val f = chainActor ? message
    val result = Await.ready(f, timeout).value.get
    result match {
      case Success(data) => Right(data)
      case Failure(data) => Left(data)
    }
  }

}
