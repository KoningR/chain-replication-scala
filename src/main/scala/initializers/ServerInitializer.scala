package initializers

import actors.Server
import actors.Server.InitServer
import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, Terminated}
import com.typesafe.config.ConfigFactory

object ServerInitializer {

  def apply(): Behavior[NotUsed] =
    Behaviors.setup { context => {
      val server = context.spawn(Server(), "CRS")
      server ! InitServer()

      Behaviors.receiveSignal {
        case (_, Terminated(_)) =>
          context.log.info("Stopping the system.")
          Behaviors.stopped
      }

    }
    }

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    ActorSystem(ServerInitializer(), "CRS", config.getConfig("server").withFallback(config))
  }

}
