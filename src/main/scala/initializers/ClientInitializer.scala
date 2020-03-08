package initializers

import actors.Client
import actors.Client.InitClient
import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, Terminated}
import com.typesafe.config.ConfigFactory

object ClientInitializer {

  final val SERVER_PATH: String = "akka://CRS@127.0.0.1:1000/user/CRS"

  def apply(): Behavior[NotUsed] =
    Behaviors.setup { context => {
      val client = context.spawn(Client(), "CRS")
      client ! InitClient(SERVER_PATH)

      Behaviors.receiveSignal {
        case (_, Terminated(_)) =>
          context.log.info("Stopping the system")
          Behaviors.stopped
      }
    }
    }

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    ActorSystem(ClientInitializer(), "CRS", config.getConfig("client").withFallback(config))
  }

}
