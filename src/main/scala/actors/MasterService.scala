package actors

import actors.Client.{ChainInfoResponse, ClientReceivable}
import actors.Server.{ChainPositionUpdate, RegisteredServer, ServerReceivable}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import communication.JsonSerializable

object MasterService {

    sealed trait MasterServiceReceivable extends JsonSerializable
    final case class InitMasterService() extends MasterServiceReceivable
    final case class RequestChainInfo(replyTo: ActorRef[ClientReceivable]) extends MasterServiceReceivable
    final case class RegisterServer(replyTo: ActorRef[ServerReceivable]) extends MasterServiceReceivable
    final case class RequestChainPosition(replyTo: ActorRef[ServerReceivable]) extends MasterServiceReceivable

    private var chain = List[ActorRef[ServerReceivable]]()

    def apply(): Behavior[MasterServiceReceivable] = Behaviors.receive {
        (context, message) => {
            message match {
                case InitMasterService() => initMasterService(context, message)
                case RegisterServer(replyTo) => registerServer(context, message, replyTo)
                case RequestChainInfo(replyTo) => requestChainInfo(context, message, replyTo)
                case RequestChainPosition(replyTo) => requestChainPosition(context, message, replyTo)
            }
        }
    }

    def initMasterService(context: ActorContext[MasterServiceReceivable], message: MasterServiceReceivable): Behavior[MasterServiceReceivable] = {
        context.log.info("MasterService: master service is initialized.")
        Behaviors.same
    }

    def registerServer(context: ActorContext[MasterServiceReceivable], message: MasterServiceReceivable, replyTo: ActorRef[ServerReceivable]): Behavior[MasterServiceReceivable] = {
        // Always add new server to the head of the chain
        chain = replyTo :: chain

        replyTo ! RegisteredServer(context.self)

        // TODO: can be optimized to only send update to the neighbours of this server
        chain.zipWithIndex.foreach{ case (server, index) => chainPositionUpdate(context, server, index) }

        context.log.info("MasterService: received a register request from a server, sent response.")
        Behaviors.same
    }

    def requestChainInfo(context: ActorContext[MasterServiceReceivable], message: MasterServiceReceivable, replyTo: ActorRef[ClientReceivable]): Behavior[MasterServiceReceivable] = {
        replyTo ! ChainInfoResponse(chain.head, chain.last)

        context.log.info("MasterService: received a chain request from a client, sent info.")
        Behaviors.same
    }

    def requestChainPosition(context: ActorContext[MasterServiceReceivable], message: MasterServiceReceivable, replyTo: ActorRef[ServerReceivable]): Behavior[MasterServiceReceivable] = {
        val index = chain.indexOf(replyTo)
        chainPositionUpdate(context, replyTo, index)

        context.log.info("MasterService: received a chain position request from a server, sent position.")
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
