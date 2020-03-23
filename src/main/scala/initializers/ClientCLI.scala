package initializers

import actors.Client.{CallQuery, CallUpdate}
import akka.NotUsed
import akka.actor.ActorSelection
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
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

                var input = StdIn.readLine("Please input something to do:")
                while (!input.equals("quit") || !input.equals("q")) {

                    val command: String = input.split(" ").map(_.trim).toList(0)

                    command match {
                        case "query" =>
                            println("Query command called.")
                            val inputList: List[String] = input.split(" ").map(_.trim).filter(_.length > 0).toList

                            inputList match {
                                case _ :: objId :: options =>
                                    println("   Object ID: " + objId)
                                    println("   Options: ")
                                    options.foreach(println)
                                    val optionsParameter = options match {
                                        case Nil => None
                                        case list => Some(list)
                                    }
                                    client ! CallQuery(objId.toInt, optionsParameter)
                            }
                        case "update" =>
                            println("Update command called.")
                            val newObj = input.substring(input.indexOf("{"), input.lastIndexOf("}") + 1)
                            val inputWithoutObject = input.replace(newObj, "")
                            val inputWithoutObjectList: List[String] = inputWithoutObject.split(" ").map(_.trim).filter(_.length > 0).toList

                            inputWithoutObjectList match {
                                case _ :: objId :: options =>
                                    println("   Object ID : " + objId)
                                    println("   New Object: " + newObj)
                                    println("   Options: ")
                                    options.foreach(println)
                                    val optionsParameter = options match {
                                        case Nil => None
                                        case list => Some(list)
                                    }
                                    client ! CallUpdate(objId.toInt, newObj, optionsParameter)
                            }
                        case _ => println("Command was not valid.")
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
