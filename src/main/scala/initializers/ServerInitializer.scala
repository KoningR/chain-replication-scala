package initializers

import actors.Server
import actors.Server.InitServer
import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, Terminated}
import com.typesafe.config.ConfigFactory

object ServerInitializer {

  final val MASTER_SERVICE_PATH: String = "akka://CRS@127.0.0.1:3000/user/CRS"

  def apply(): Behavior[NotUsed] =
    Behaviors.setup { context => {
      val server = context.spawn(Server(), "CRS")
      server ! InitServer(MASTER_SERVICE_PATH)

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
