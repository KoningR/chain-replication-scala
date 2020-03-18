package initializers

import actors.Client.{CallQuery, CallUpdate}
import akka.actor.typed.scaladsl.adapter._
import akka.NotUsed
import akka.actor.ActorSelection
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import com.typesafe.config.ConfigFactory

import scala.io.StdIn


object ClientCLI {

    final val CLIENT_PATH: String = "akka://CRS@127.0.0.1:2000/user/CRS"
    var client: ActorSelection = _

    def apply(): Behavior[NotUsed] =
        Behaviors.setup {
            context => {
                this.client = context.toClassic.actorSelection(CLIENT_PATH)

                var input = StdIn.readLine("Please input something to do: ")
                while (!input.equals("quit") || !input.equals("q")) {
                    println(input)

                    val output: List[String] = input.split(",").map(_.trim).filter(_.length > 0).toList

                    output match {
                        case "query" :: objId :: options =>
                            println("  Query called")
                            if (objId.forall(_.isDigit)) {
                                println("Object id: " + objId)
                                println("  Options: ")
                                options.foreach(println)
                                client ! CallQuery(objId.toInt, Option(options))
                            } else {
                                println("Object ID invalid")
                            }
                        case "update" :: objId :: newObj :: options =>
                            println("  Update called")
                            if (objId.forall(_.isDigit)) {
                                println("Object id : " + objId)
                                println("New object: " + newObj)
                                println("  Options: ")
                                options.foreach(println)
                                client ! CallUpdate(objId.toInt, newObj, Option(options))
                            } else {
                                println("Object ID invalid")
                            }
                        case _ => println("Unknown output " + output)
                    }

                    input = StdIn.readLine("Please input something to do: ")
                }

                Behaviors.stopped
            }
        }

    def main(args: Array[String]): Unit = {
        val config = ConfigFactory.load()
        ActorSystem(ClientCLI(), "CRS", config.getConfig("cli").withFallback(config))
    }

}
