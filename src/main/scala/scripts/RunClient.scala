package scripts

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import core.{Client, Server}

object RunClient {
  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    val system = ActorSystem("ChainReplication", config.getConfig("client").withFallback(config))
    val client = system.actorOf(Props[Client], name = "client")

    client ! "hello"
  }
}
