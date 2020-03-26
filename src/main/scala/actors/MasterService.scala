package actors

import actors.Client.{ChainInfoResponse, ClientReceivable}
import actors.Server.{ChainPositionUpdate, RegisteredServer, ServerReceivable, TransferDatabase}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import communication.JsonSerializable

object MasterService {

    sealed trait MasterServiceReceivable extends JsonSerializable
    final case class InitMasterService() extends MasterServiceReceivable
    final case class RequestChainInfo(replyTo: ActorRef[ClientReceivable]) extends MasterServiceReceivable
    final case class RegisterServer(replyTo: ActorRef[ServerReceivable]) extends MasterServiceReceivable
    final case class RegisterTail(replyTo: ActorRef[ServerReceivable]) extends MasterServiceReceivable

    private var chain = List[ActorRef[ServerReceivable]]()
    private var potentialTails = List[ActorRef[ServerReceivable]]()

    def apply(): Behavior[MasterServiceReceivable] = Behaviors.receive {
        (context, message) => {
            message match {
                case InitMasterService() => initMasterService(context, message)
                case RegisterServer(replyTo) => registerServer(context, message, replyTo)
                case RequestChainInfo(replyTo) => requestChainInfo(context, message, replyTo)
                case RegisterTail(replyTo) => registerTail(context, replyTo)
            }
        }
    }

    def initMasterService(context: ActorContext[MasterServiceReceivable], message: MasterServiceReceivable): Behavior[MasterServiceReceivable] = {
        context.log.info("MasterService: master service is initialized.")
        Behaviors.same
    }

    def registerServer(context: ActorContext[MasterServiceReceivable], message: MasterServiceReceivable, replyTo: ActorRef[ServerReceivable]): Behavior[MasterServiceReceivable] = {
        if (chain.isEmpty) {
            // Add head to chain initially.
            chain = chain :+ replyTo
        } else {
            // Otherwise, new server is potential chain.
            potentialTails = potentialTails :+ replyTo
            chain.last ! TransferDatabase(replyTo)
        }

        replyTo ! RegisteredServer(context.self)

        // Send chainPositionUpdate to all the servers in the chain
        chain.zipWithIndex.foreach{ case (server, index) => chainPositionUpdate(context, server, index) }

        context.log.info("MasterService: received a register request from a server, sent response.")
        Behaviors.same
    }

    def registerTail(context: ActorContext[MasterServiceReceivable], replyTo: ActorRef[ServerReceivable]): Behavior[MasterServiceReceivable] = {
        // Remove server from potential tails.
        potentialTails = potentialTails.filter(_ != replyTo)

        // Add server to chain as tail.
        chain = chain :+ replyTo

        // Send chainPositionUpdate to all the servers in the chain
        chain.zipWithIndex.foreach{ case (server, index) => chainPositionUpdate(context, server, index) }

        context.log.info("MasterService: registering tail, sent response.")
        Behaviors.same
    }

    def requestChainInfo(context: ActorContext[MasterServiceReceivable], message: MasterServiceReceivable, replyTo: ActorRef[ClientReceivable]): Behavior[MasterServiceReceivable] = {
        replyTo ! ChainInfoResponse(chain.head, chain.last)

        context.log.info("MasterService: received a chain request from a client, sent info.")
        Behaviors.same
    }

    def chainPositionUpdate(context: ActorContext[MasterServiceReceivable],
                                 server: ActorRef[ServerReceivable], index: Int): Unit = {
        val isHead = index == 0
        val isTail = index == chain.length - 1
        val previous = chain(Math.max(index - 1, 0))
        val next = chain(Math.min(index + 1, chain.length - 1))
        context.log.info("MasterService sent {} chain position: isHead: {}, isTail: {}, previous: {} and next: {}", server, isHead, isTail, previous, next)
        server ! ChainPositionUpdate(isHead, isTail, previous, next)
    }
}
