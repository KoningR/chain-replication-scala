package actors

import actors.Client.{ClientReceivable, QueryResponse, UpdateResponse}
import actors.MasterService.{MasterServiceReceivable, RegisterServer, RegisterTail}
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

    final case class TransferDatabase(newTail: ActorRef[ServerReceivable]) extends ServerReceivable

    final case class TransferUpdate(objId: Int, obj: String, replyTo: ActorRef[ServerReceivable]) extends ServerReceivable

    final case class TransferAck(objId: Int) extends ServerReceivable

    final case class TransferComplete() extends ServerReceivable

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

    private var newTailProcess = false
    private var newTail: ActorRef[Server.ServerReceivable] = _
    private var unSentDatabase: List[(Int, String)] = List()


    def apply(): Behavior[ServerReceivable] = Behaviors.receive {
        (context, message) =>
            message match {
                case InitServer(remoteMasterServicePath) => initServer(context, message, remoteMasterServicePath)
                case RegisteredServer(masterService) => registeredServer(context, message, masterService)
                case Update(objId, newObj, options, from, self) => {
                    if (!this.isTail) {
                        inProcess = Update(objId, newObj, options, from, next) :: inProcess
                    }
                    update(context, message, objId, newObj, options, from)
                }
                case UpdateAcknowledgement(objId, newObj, next) => {
                    processAcknowledgement(UpdateAcknowledgement(objId, newObj, next), context.self)
                }
                case Query(objId, options, from) => query(context, message, objId, options, from)
                case ChainPositionUpdate(isHead, isTail, previous, next) => chainPositionUpdate(context, message, isHead, isTail, previous, next)
                case TransferDatabase(newTail) => initTransferDatabase(newTail, context)
                case TransferUpdate(objId, obj, replyTo) => transferUpdate(objId, obj, replyTo)
                case TransferAck(objId) => transferAck(objId, context)
                case TransferComplete() => transferComplete(context)
            }
    }

    def initTransferDatabase(newTail: ActorRef[Server.ServerReceivable], context: ActorContext[ServerReceivable]): Behavior[ServerReceivable] = {

        this.newTailProcess = true
        this.newTail = newTail

        unSentDatabase = storage.getAllObjects

        newTail ! TransferUpdate(unSentDatabase.head._1, unSentDatabase.head._2, context.self)
        Behaviors.same

    }

    def transferUpdate(i: Int, str: String, replyTo: ActorRef[Server.ServerReceivable]): Behavior[ServerReceivable] = {
        storage.update(i, str, None)
        replyTo ! TransferAck(i)
        Behaviors.same
    }

    def transferAck(objId: Int, context: ActorContext[ServerReceivable]): Behavior[ServerReceivable] = {
        if (unSentDatabase.nonEmpty) {
            unSentDatabase = unSentDatabase.drop(1)
            newTail ! TransferUpdate(unSentDatabase.head._1, unSentDatabase.head._2, context.self)
        } else {
            newTail ! TransferComplete()

            // TODO: do this in a smarter way.
            inProcess.foreach(x => {
                newTail ! x
            })

            // TODO: reset! newTailProcess/newTail
        }

        Behaviors.same
    }

    def transferComplete(context: ActorContext[ServerReceivable]): Behavior[ServerReceivable] = {
        masterService ! RegisterTail(context.self)
        Behaviors.same
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
                    from ! UpdateResponse(objId, res)
                    this.previous ! UpdateAcknowledgement(objId, res, context.self)

                    if (newTailProcess) {
                        context.log.info("Server: add to inProcessForNewTail for objId {} = {} as {}.", objId, res, context.self)
                        inProcess = Update(objId, res, options, from, this.next) :: inProcess
                    }

                    context.log.info("Server: sent a update response for objId {} = {} as {}.", objId, res, context.self)
                } else {
                    context.log.info("Server: forward a update response for objId {} = {} as {} to {}.", objId, res, context.self, this.next)
                    this.next ! Update(objId, res, options, from, this.next)
                }
            case None =>
                context.log.info("Something went wrong while updating {}", objId)
        }

        Behaviors.same
    }

    def processAcknowledgement(ack: UpdateAcknowledgement, self: ActorRef[ServerReceivable]): Behavior[ServerReceivable] = {
        val results = inProcess.filter {
            case Update(objId, newObj, options, from, previous) => !(objId == ack.objId && newObj == ack.newObj)
            case _ => true
        }
        inProcess = results
        if (!this.isHead) {
            this.previous ! UpdateAcknowledgement(ack.objId, ack.newObj, self)
        }
        Behaviors.same
    }

}
