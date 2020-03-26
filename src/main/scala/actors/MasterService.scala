package actors

import actors.Client.{ChainInfoResponse, ClientReceivable}
import actors.Server.{ChainPositionUpdate, RegisteredServer, ServerReceivable}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.dispatch.ExecutionContexts
import communication.JsonSerializable

import scala.concurrent.duration._

object MasterService {

    sealed trait MasterServiceReceivable extends JsonSerializable
    final case class InitMasterService() extends MasterServiceReceivable
    final case class RequestChainInfo(replyTo: ActorRef[ClientReceivable]) extends MasterServiceReceivable
    final case class RegisterServer(replyTo: ActorRef[ServerReceivable]) extends MasterServiceReceivable
    final case class Remove(server: ActorRef[ServerReceivable]) extends MasterServiceReceivable
    final case class Heartbeat(server: ActorRef[ServerReceivable]) extends MasterServiceReceivable

    private var chain = List[ActorRef[ServerReceivable]]()
    private var activeServers = Map[ActorRef[ServerReceivable], Boolean]()

    def apply(): Behavior[MasterServiceReceivable] = Behaviors.receive {
        (context, message) => {
            message match {
                case InitMasterService() => initMasterService(context, message)
                case RegisterServer(replyTo) => registerServer(context, message, replyTo)
                case RequestChainInfo(replyTo) => requestChainInfo(context, message, replyTo)
                case Heartbeat(replyTo) => heartbeat(context, message, replyTo)
            }
        }
    }

    def initMasterService(context: ActorContext[MasterServiceReceivable], message: MasterServiceReceivable): Behavior[MasterServiceReceivable] = {
        context.log.info("MasterService: master service is initialized.")

        // Remove inactive servers every 5 seconds
        context.system.scheduler.scheduleAtFixedRate(0.seconds, 5.seconds)(
            () => removeInactiveServers(context)
        )(ExecutionContexts.global())

        Behaviors.same
    }

    def registerServer(context: ActorContext[MasterServiceReceivable], message: MasterServiceReceivable, replyTo: ActorRef[ServerReceivable]): Behavior[MasterServiceReceivable] = {
        // Always add new server to the tail of the chain
        chain = chain :+ replyTo
        activeServers = activeServers.updated(replyTo, true)

        replyTo ! RegisteredServer(context.self)

        // Send chainPositionUpdate to all the servers in the chain
        chain.zipWithIndex.foreach{ case (server, index) => chainPositionUpdate(context, server, index) }

        context.log.info("MasterService: received a register request from a server, sent response.")

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

    def heartbeat(value: ActorContext[MasterServiceReceivable], receivable: MasterServiceReceivable, replyTo: ActorRef[Server.ServerReceivable]): Behavior[MasterServiceReceivable] = {
        activeServers = activeServers.updated(replyTo, true)

        Behaviors.same
    }

    def removeInactiveServers(context: ActorContext[MasterServiceReceivable]): Unit = {
        // Get all inactive servers
        val toRemove = chain.filter(actorRef => {
            val isActive = activeServers.get(actorRef)
            isActive match {
                case Some(true) => false
                case _ => true
            }
        })

        // Remove inactive servers from the chain and update all other servers
        if (toRemove.nonEmpty) {
            context.log.info("MasterService: Removing servers due to failing heartbeats. {}", toRemove)
            chain = chain.filter(actorRef => !toRemove.contains(actorRef))
            activeServers = activeServers.removedAll(toRemove)
            chain.zipWithIndex.foreach{ case (server, index) => chainPositionUpdate(context, server, index) }
        }

        // Reset activeServers
        activeServers = activeServers.empty
    }
}
