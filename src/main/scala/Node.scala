package dk.diku.blockchain

import akka.actor.ActorSystem
import akka.actor.Props

class Node(port: Int) {

  val system = ActorSystem("bc-system")

  val myActor = system.actorOf(Props[ChainActor], "chain")

}
