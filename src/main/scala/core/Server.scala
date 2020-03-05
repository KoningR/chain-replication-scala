package core

import akka.actor.Actor

class Server extends Actor {
  override def receive: Receive = {
    case "hello" =>
      println("hello back at you. I am: ")
      println(context.self.toString())

    case _ => println("huh?")
  }
}