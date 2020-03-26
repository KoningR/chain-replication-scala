package actors

import actors.Client.{ClientReceivable, QueryResponse, UpdateResponse}
import actors.MasterService.{Heartbeat, MasterServiceReceivable, RegisterServer}
import akka.actor.ActorSelection
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.dispatch.ExecutionContexts
import com.typesafe.config.ConfigFactory
import communication.JsonSerializable
import storage.Storage
import ujson.IndexedValue.True

import scala.concurrent.duration._

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
                    if(!this.isTail) {
                        inProcess = Update(objId, newObj, options, from, next) :: inProcess
                    }
                    update(context, message, objId, newObj, options, from)
                }
                case UpdateAcknowledgement(objId, newObj, next) => {
                    processAcknowledgement(UpdateAcknowledgement(objId, newObj, next), context.self)
                }
                case Query(objId, options, from) => query(context, message, objId, options, from)
                case ChainPositionUpdate(isHead, isTail, previous, next) => chainPositionUpdate(context, message, isHead, isTail, previous, next)
            }
    }

    def initServer(context: ActorContext[ServerReceivable], message: ServerReceivable, remoteMasterServicePath: String): Behavior[ServerReceivable] = {
        masterService = context.toClassic.actorSelection(remoteMasterServicePath)
        // TODO: Check if masterService is defined and stop the server if not.

        val fileName = context.self.path.toStringWithAddress(context.system.address).hashCode.toString
        storage = new Storage(fileName)

        masterService ! RegisterServer(context.self)

        context.log.info("Server: registering server at master service.")
        Behaviors.same
    }

    def registeredServer(context: ActorContext[ServerReceivable], message: ServerReceivable, masterService: ActorRef[MasterServiceReceivable]): Behavior[ServerReceivable] = {
        context.log.info("Server: server is registered at {}.", masterService.path)

        // Send a heartbeat to the masterservice every 2 seconds.
        context.system.scheduler.scheduleAtFixedRate(0.seconds, 2.seconds)(() => {
            masterService ! Heartbeat(context.self)
        })(ExecutionContexts.global())

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

        if(!this.isTail){
            forwardUpdates(context)
        }
        Behaviors.same
    }

    def query(context: ActorContext[ServerReceivable], message: ServerReceivable, objId: Int, options: Option[List[String]], from: ActorRef[ClientReceivable]): Behavior[ServerReceivable] = {
        val result = storage.query(objId, options)
        result match {
            case Some(res) =>
                from ! QueryResponse(objId, res)
                context.log.info("Server: sent a query response for objId {} = {}.", objId, res)
            case None =>
                context.log.info("No result found for objId {}", objId)
        }
        Behaviors.same
    }


    def update(context: ActorContext[ServerReceivable], message: ServerReceivable, objId: Int, newObj: String, options: Option[List[String]], from: ActorRef[ClientReceivable]): Behavior[ServerReceivable] = {
        val result = storage.update(objId, newObj, options)
        result match {
            case Some(res) =>
                if (this.isTail) {
                    from ! UpdateResponse(objId, newObj)
                    this.previous ! UpdateAcknowledgement(objId, newObj, context.self)
                    context.log.info("Server: sent a update response for objId {} = {} as {}.", objId, newObj, context.self)
                } else {
                    context.log.info("Server: forward a update response for objId {} = {} as {} to {}.", objId, newObj, context.self, this.next)
                    this.next ! Update(objId, newObj, options, from, this.next)
                }
            case None =>
                context.log.info("Something went wrong while updating {}", objId)
        }

        Behaviors.same
    }

    def processAcknowledgement(ack: UpdateAcknowledgement, self: ActorRef[ServerReceivable]) : Behavior[ServerReceivable] = {
        val results = inProcess.filter {
            case Update(objId, newObj, options, from, previous) => !(objId==ack.objId && newObj==ack.newObj)
            case _ => true
        }
        inProcess = results
        if (!this.isHead) {
            this.previous ! UpdateAcknowledgement(ack.objId, ack.newObj, self)
        }
        Behaviors.same
    }

    def forwardUpdates(context: ActorContext[ServerReceivable]) = {
        inProcess.foreach(x => {
            this.next ! x
            context.log.info("forwarding {}", x)
        })
    }

}
