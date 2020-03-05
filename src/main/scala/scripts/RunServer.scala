package scripts

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import core.Server

object RunServer {
  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    val system = ActorSystem("ChainReplication", config.getConfig("server").withFallback(config))
    val server = system.actorOf(Props[Server], name = "server")

    server ! "hello"
  }
}