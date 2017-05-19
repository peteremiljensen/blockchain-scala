package dk.diku.freechain

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import scala.language.postfixOps

class Events(_fn: Any => Unit)
  (implicit system: ActorSystem, validator: Validator) {

  val fn = _fn

  private val eventsActor =
    system.actorOf(Props(new EventsActor(this)), "events")
}

class EventsActor(events: Events)(implicit validator: Validator)
    extends Actor with ActorLogging {

  import Events._

  override def receive: Receive = LoggingReceive {
    case msg => events.fn(msg)
  }
}

object Events {
  case object ConnectionReady
  case object ChainReplaced
  case class LoafAdded(loaf: Loaf)
  case class BlockAdded(block: Block)
}
