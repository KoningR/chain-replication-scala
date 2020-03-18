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

    final case class RegisteredServer(masterService: ActorRef[MasterServiceReceivable],
                                      previous: ActorRef[ServerReceivable],
                                      next: ActorRef[ServerReceivable],
                                      isHead: Boolean,
                                      isTail: Boolean
                                     ) extends ServerReceivable

    final case class Query(objId: Int, options: Option[List[String]], from: ActorRef[ClientReceivable]) extends ServerReceivable

    final case class Update(objId: Int, newObj: String, options: Option[List[String]], from: ActorRef[ClientReceivable], previous: ActorRef[ServerReceivable]) extends ServerReceivable

    final case class UpdateAcknowledgement(objId: Int, newObj: String, next: ActorRef[ServerReceivable]) extends ServerReceivable

    private var inProcess: List[ServerReceivable] = List()
    private var masterService: ActorSelection = _
    private var storage: Storage = _

    def apply(): Behavior[ServerReceivable] = Behaviors.receive {
        (context, message) =>
            message match {
                case InitServer(remoteMasterServicePath) => initServer(context, message, remoteMasterServicePath)
                case RegisteredServer(masterService, previous, next, isHead, isTail) => registeredServer(context, message, masterService, previous, next, isHead, isTail)
                case Update(objId, newObj, options, from, self) => {
                    inProcess = Update(objId, newObj, options, from, successor) :: inProcess
                    update(context, message, objId, newObj, options, from, successor)
                }
                case UpdateAcknowledgement(objId, newObj, successor) => {
                    processAcknowledgement()
                    Behaviors.same
                }
                case Query(objId, options, from) => query(context, message, objId, options, from)
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

    def registeredServer(context: ActorContext[ServerReceivable], message: ServerReceivable, masterService: ActorRef[MasterServiceReceivable],
                         previous: ActorRef[ServerReceivable], next: ActorRef[ServerReceivable], isHead: Boolean, isTail: Boolean): Behavior[ServerReceivable] = {

        context.log.info("Server: server is registered at {}.", masterService.path)
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
        result match {
            case _ => {
                // TODO: if this server is the tail, send UpdateReponse to Client
                previous ! UpdateAcknowledgement(objId, newObj, context.self)
            }
        }
        context.log.info("Server: sent a update response for objId {} = {}.", objId, newObj)
        Behaviors.same
    }

    def processAcknowledgement(): Unit = {
        // TODO: remove Update from imProcess List.
    }

}
