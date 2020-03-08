package actors

import actors.Client.{ClientReceivable, PongClient, QueryResponse}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object Server {

  sealed trait ServerReceivable
  final case class InitServer() extends ServerReceivable
  final case class PingServer(replyTo: ActorRef[ClientReceivable]) extends ServerReceivable
  final case class Query(objId: Int, from: ActorRef[ClientReceivable]) extends ServerReceivable

  def apply(): Behavior[ServerReceivable] = Behaviors.receive {
    (context, message) =>
      message match {
        case InitServer() =>
          context.log.info("Server: server is initialized.")
          Behaviors.same
        case Query(objId, from) =>
          context.log.info("Server: sending a response for objId {} = {}.", objId, "Apple")
          from ! QueryResponse(objId, "Apple")
          Behaviors.stopped
        case PingServer(replyTo) =>
          context.log.info("Server: received a ping from a client, sending a pong back.")
          replyTo ! PongClient(context.self)
          Behaviors.same
      }
  }

}
