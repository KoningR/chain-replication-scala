package actors

import actors.Client.{ChainInfoResponse, ClientReceivable, PongClient}
import actors.Server.{RegisteredServer, ServerReceivable}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object MasterService {

    sealed trait MasterServiceReceivable

    final case class InitMasterService() extends MasterServiceReceivable

    final case class PingMasterService(replyTo: ActorRef[ClientReceivable]) extends MasterServiceReceivable

    final case class RequestChainInfo(replyTo: ActorRef[ClientReceivable]) extends MasterServiceReceivable

    final case class RegisterServer(replyTo: ActorRef[ServerReceivable]) extends MasterServiceReceivable

    var head: ActorRef[ServerReceivable] = _
    var tail: ActorRef[ServerReceivable] = _

    def apply(): Behavior[MasterServiceReceivable] = Behaviors.receive {
        (context, message) =>
            message match {
                case InitMasterService() =>
                    context.log.info("MasterService: master service is initialized.")
                    Behaviors.same
                case PingMasterService(replyTo) =>
                    context.log.info("MasterService: received a register request from a server, sending response.")
                    replyTo ! PongClient(context.self)
                    Behaviors.same
                case RegisterServer(replyTo) =>
                    context.log.info("MasterService: received a register request from a server, sending response.")
                    this.head = replyTo
                    this.tail = replyTo
                    replyTo ! RegisteredServer(context.self)
                    Behaviors.same
                case RequestChainInfo(replyTo) =>
                    context.log.info("MasterService: received a chain request from a client, sending info.")
                    replyTo ! ChainInfoResponse(this.head, this.tail)
                    Behaviors.same
            }
    }

}
