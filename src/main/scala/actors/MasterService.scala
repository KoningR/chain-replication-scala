package actors

import actors.Client.{ChainInfoResponse, ClientReceivable}
import actors.Server.{RegisteredServer, ServerReceivable}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import communication.JsonSerializable

import scala.concurrent.duration._

object MasterService {

    sealed trait MasterServiceReceivable extends JsonSerializable
    final case class InitMasterService() extends MasterServiceReceivable
    final case class RequestChainInfo(replyTo: ActorRef[ClientReceivable]) extends MasterServiceReceivable
    final case class RegisterServer(replyTo: ActorRef[ServerReceivable]) extends MasterServiceReceivable

    final case class Remove(server: ActorRef[ServerReceivable]) extends MasterServiceReceivable

    private var chain = List[ActorRef[ServerReceivable]]()

    def apply(): Behavior[MasterServiceReceivable] = Behaviors.receive {
        (context, message) => {
            message match {
                case InitMasterService() => initMasterService(context, message)
                case RegisterServer(replyTo) => registerServer(context, message, replyTo)
                case RequestChainInfo(replyTo) => requestChainInfo(context, message, replyTo)
            }
        }
    }

    def initMasterService(context: ActorContext[MasterServiceReceivable], message: MasterServiceReceivable): Behavior[MasterServiceReceivable] = {
        context.log.info("MasterService: master service is initialized.")
        Behaviors.same
    }

    def registerServer(context: ActorContext[MasterServiceReceivable], message: MasterServiceReceivable, replyTo: ActorRef[ServerReceivable]): Behavior[MasterServiceReceivable] = {
        chain = replyTo :: chain

        replyTo ! RegisteredServer(context.self, replyTo, replyTo, isHead = true, isTail = true)

        context.log.info("MasterService: received a register request from a server, sent response.")

        // Timeout the newly added server after two seconds of not receiving a heartbeat.
        Behaviors.withTimers[MasterServiceReceivable] {
            timers => {
                // Send a Remove(server) message delayed by 2 seconds.
                timers.startSingleTimer(Remove(replyTo), Remove(replyTo), 2.seconds)

                // Receive the delayed message and remove the server from the chain.
                Behaviors.receiveMessagePartial {
                    case Remove(server) => removeServerFromChain(context, server)
                }
            }
        }
    }

    def removeServerFromChain(context: ActorContext[MasterServiceReceivable], server: ActorRef[ServerReceivable]): Behavior[MasterServiceReceivable] = {
        context.log.info("MasterService: Removing a server due to failed heartbeat.")
        Behaviors.same
    }

    def requestChainInfo(context: ActorContext[MasterServiceReceivable], message: MasterServiceReceivable, replyTo: ActorRef[ClientReceivable]): Behavior[MasterServiceReceivable] = {
        replyTo ! ChainInfoResponse(chain.head, chain.last)

        context.log.info("MasterService: received a chain request from a client, sent info.")
        Behaviors.same
    }
}
