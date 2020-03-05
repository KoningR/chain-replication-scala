package core

import akka.actor.{Actor, ActorRef, ActorSelection}

import scala.concurrent.Await

class Client extends Actor {

  override def receive: Receive = {
    case "hello" =>
      println("hello from the client")
//      val remote: ActorSelection = context.actorSelection("akka.tcp://ChainReplication@127.0.0.1:2552/user/server")
//      remote ! "hello"

    case _       =>
      println("unknown message")
  }
}
