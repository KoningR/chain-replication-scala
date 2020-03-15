package actors

import actors.Client.{ClientReceivable, QueryResponse, UpdateResponse}
import actors.MasterService.{MasterServiceReceivable, RegisterServer}
import akka.actor.ActorSelection
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, Behavior}

object Server {

    sealed trait ServerReceivable
    final case class InitServer(remoteMasterServicePath: String) extends ServerReceivable
    final case class RegisteredServer(masterService: ActorRef[MasterServiceReceivable],
                                      previous: ActorRef[ServerReceivable],
                                      next: ActorRef[ServerReceivable],
                                      isHead: Boolean,
                                      isTail: Boolean
                                     ) extends ServerReceivable
    final case class Query(objId: Int, from: ActorRef[ClientReceivable]) extends ServerReceivable
    final case class Update(objId: Int, newObj: String, from : ActorRef[ClientReceivable], previous: ActorRef[ServerReceivable]) extends ServerReceivable
    final case class UpdateAcknowledgement(objId: Int, newObj: String, next: ActorRef[ServerReceivable]) extends ServerReceivable

    private var masterService: ActorSelection = _
    private var inProcess: List[ServerReceivable] = List()

    def apply(): Behavior[ServerReceivable] = Behaviors.receive {
        (context, message) =>
            message match {
                case InitServer(remoteMasterServicePath) => initServer(context, message, remoteMasterServicePath)
                case RegisteredServer(masterService, previous, next, isHead, isTail) => registeredServer(context, message, masterService, previous, next, isHead, isTail)
                case Query(objId, from) => {
                    query(context, message, objId, from)
                }
                case Update(objId, newObj, from, previous) => {
                    inProcess = Update(objId, newObj, previous) :: inProcess
                    update(context, message, objId, newObj, from, previous)
                }
                case UpdateAcknowledgement(objId, newObj, next) => {
                    processAcknowledgement()
                    Behaviors.same
                }
            }
    }

    def initServer(context: ActorContext[ServerReceivable], message: ServerReceivable, remoteMasterServicePath: String): Behavior[ServerReceivable] = {
        masterService = context.toClassic.actorSelection(remoteMasterServicePath)
        masterService ! RegisterServer(context.self)
        // TODO: Check if masterService is defined and stop the server if not.

        context.log.info("Server: registering server at master service.")
        Behaviors.same
    }

    def registeredServer(context: ActorContext[ServerReceivable], message: ServerReceivable, masterService: ActorRef[MasterServiceReceivable],
                         previous: ActorRef[ServerReceivable], next: ActorRef[ServerReceivable], isHead: Boolean, isTail: Boolean): Behavior[ServerReceivable] = {

        context.log.info("Server: server is registered at {}.", masterService.path)
        Behaviors.same
    }

    def query(context: ActorContext[ServerReceivable], message: ServerReceivable, objId: Int, from: ActorRef[ClientReceivable]): Behavior[ServerReceivable] = {
        from ! QueryResponse(objId, "Apple")

        context.log.info("Server: sent a query response for objId {} = {}.", objId, "Apple")
        Behaviors.same
    }

    def update(context: ActorContext[ServerReceivable], message: ServerReceivable, objId: Int, newObj: String, from :ActorRef[ClientReceivable], previous: ActorRef[ServerReceivable]): Behavior[ServerReceivable] = {
        // TODO: if this server is the tail, send UpdateReponse to Client
        previous ! UpdateAcknowledgement(objId, newObj, context.self)
        
        context.log.info("Server: sent a update response for objId {} = {}.", objId, newObj)
        Behaviors.same
    }

    def processAcknowledgement() : Unit = {
        // TODO: remove Update from imProcess List.
    }

}
