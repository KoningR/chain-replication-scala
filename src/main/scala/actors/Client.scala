package actors

import actors.Server.{PingServer, Query, ServerReceivable}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, Behavior}

object Client {

  sealed trait ClientReceivable
  final case class InitClient(remoteServerPath: String) extends ClientReceivable
  final case class QueryResponse(objId: Int, obj: String) extends ClientReceivable
  final case class PongClient(server: ActorRef[ServerReceivable]) extends ClientReceivable


  def apply(): Behavior[ClientReceivable] = Behaviors.receive {
    (context, message) =>
      message match {
        case InitClient(remoteServerPath) =>
          val server = context.toClassic.actorSelection(remoteServerPath)
          context.log.info("Client: will try to discover server {} by pinging it.", server.pathString)
          server ! PingServer(context.self)
          Behaviors.same
        case PongClient(server) =>
          context.log.info("Client: will has discovered server! Will send a query.")
          server ! Query(1, context.self)
          Behaviors.same
        case QueryResponse(objId, obj) =>
          context.log.info("Client: received a QueryResponse for objId {} = {}", objId, obj)
          Behaviors.same
      }

  }
}
