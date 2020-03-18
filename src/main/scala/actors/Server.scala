package actors

import actors.Client.{ClientReceivable, QueryResponse, UpdateResponse}
import actors.MasterService.{MasterServiceReceivable, RegisterServer}
import akka.actor.ActorSelection
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import communication.JsonSerializable
import storage.Storage

object Server {

    sealed trait ServerReceivable extends JsonSerializable
    final case class InitServer(remoteMasterServicePath: String) extends ServerReceivable
    final case class Query(objId: Int, options: Option[List[String]], from: ActorRef[ClientReceivable]) extends ServerReceivable
    final case class Update(objId: Int, newObj: String, options: Option[List[String]], from: ActorRef[ClientReceivable], previous: ActorRef[ServerReceivable]) extends ServerReceivable
    final case class UpdateAcknowledgement(objId: Int, newObj: String, next: ActorRef[ServerReceivable]) extends ServerReceivable
    final case class RegisteredServer(masterService: ActorRef[MasterServiceReceivable]) extends ServerReceivable
    final case class ChainPositionUpdate(isHead: Boolean,
                                         isTail: Boolean,
                                         previous: ActorRef[ServerReceivable],
                                         next: ActorRef[ServerReceivable]
                                        ) extends ServerReceivable





    private var inProcess: List[ServerReceivable] = List()
    private var masterService: ActorSelection = _
    private var storage: Storage = _

    private var isHead: Boolean = _
    private var isTail: Boolean = _
    private var previous: ActorRef[ServerReceivable] = _
    private var next: ActorRef[ServerReceivable] = _

    def apply(): Behavior[ServerReceivable] = Behaviors.receive {
        (context, message) =>
            message match {
                case InitServer(remoteMasterServicePath) => initServer(context, message, remoteMasterServicePath)
                case RegisteredServer(masterService) => registeredServer(context, message, masterService)
                case Update(objId, newObj, options, from, self) => {
                    inProcess = Update(objId, newObj, options, from, next) :: inProcess
                    update(context, message, objId, newObj, options, from, next)
                }
                case UpdateAcknowledgement(objId, newObj, next) => {
                    processAcknowledgement(UpdateAcknowledgement(objId, newObj, next))
                    Behaviors.same
                }
                case Query(objId, options, from) => query(context, message, objId, options, from)
                case ChainPositionUpdate(isHead, isTail, previous, next) => chainPositionUpdate(context, message, isHead, isTail, previous, next)
            }
    }

    def initServer(context: ActorContext[ServerReceivable], message: ServerReceivable, remoteMasterServicePath: String): Behavior[ServerReceivable] = {
        masterService = context.toClassic.actorSelection(remoteMasterServicePath)
        // TODO: Check if masterService is defined and stop the server if not.

        val fileName = context.self.path.name
        storage = new Storage(fileName)

        masterService ! RegisterServer(context.self)

        context.log.info("Server: registering server at master service.")
        Behaviors.same
    }

    def registeredServer(context: ActorContext[ServerReceivable], message: ServerReceivable, masterService: ActorRef[MasterServiceReceivable]): Behavior[ServerReceivable] = {
        context.log.info("Server: server is registered at {}.", masterService.path)
        Behaviors.same
    }

    def chainPositionUpdate(context: ActorContext[ServerReceivable], message: ServerReceivable,
                            isHead: Boolean, isTail: Boolean,
                            previous: ActorRef[ServerReceivable], next: ActorRef[ServerReceivable]
                           ): Behavior[ServerReceivable] = {
        context.log.info("Server: server received chain position update, isHead {}, isTail {}, previous {} and next {}.",
            isHead, isTail, previous, next)

        this.isHead = isHead
        this.isTail = isTail
        this.previous = previous
        this.next = next

        Behaviors.same
    }

    def query(context: ActorContext[ServerReceivable], message: ServerReceivable, objId: Int, options: Option[List[String]], from: ActorRef[ClientReceivable]): Behavior[ServerReceivable] = {
        val result = storage.query(objId, options)

        // TODO: handle errors (None case)
        result match {
            case _ => from ! QueryResponse(objId, result.get)
        }

        context.log.info("Server: sent a query response for objId {} = {}.", objId, "Apple")
        Behaviors.same
    }


    def update(context: ActorContext[ServerReceivable], message: ServerReceivable, objId: Int, newObj: String, options: Option[List[String]], from: ActorRef[ClientReceivable], previous: ActorRef[ServerReceivable]): Behavior[ServerReceivable] = {
        val result = storage.update(objId, newObj, options)
        // TODO: handle errors (None case)
        if (this.isHead){
            result match {
                case _ => {
                    next ! Update(objId, newObj, options, from, next)
                }
            }
        }
        else if (this.isTail){
            result match {
                case _ => {
                    from ! UpdateResponse(objId, newObj)
                    previous ! UpdateAcknowledgement(objId, newObj, context.self)
                }
            }
        }
        else {
            next ! Update(objId, newObj, options, from, next)
            previous ! UpdateAcknowledgement(objId, newObj, context.self)
        }

        context.log.info("Server: sent a update response for objId {} = {}.", objId, newObj)
        Behaviors.same
    }

    def processAcknowledgement(ack: UpdateAcknowledgement): Unit = {
        // TODO: remove Update from imProcess List.
        val results = inProcess.filter {
            case Update(objId, newObj, options, from, previous) =>{
                return !(objId==ack.objId && newObj==ack.newObj)
            }
            case _ => true
        }
        inProcess = results
    }

}
