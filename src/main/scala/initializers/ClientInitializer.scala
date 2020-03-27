package initializers

import actors.Client
import actors.Client.{CallQuery, CallUpdate, InitClient}
import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, Terminated}
import com.typesafe.config.ConfigFactory

import scala.io.StdIn

object ClientInitializer {

    final val MASTER_SERVICE_PATH: String = "akka://CRS@127.0.0.1:3000/user/CRS"

    def apply(): Behavior[NotUsed] =
        Behaviors.setup {
            context => {
                val client = context.spawn(Client(), "CRS")
                client ! InitClient(MASTER_SERVICE_PATH)

                var input = StdIn.readLine("\n")
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
                    input = StdIn.readLine("\n")
                }

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
