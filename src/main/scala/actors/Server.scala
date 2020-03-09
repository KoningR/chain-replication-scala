package actors

import actors.Client.{ClientReceivable, QueryResponse, UpdateResponse}
import actors.MasterService.{MasterServiceReceivable, RegisterServer}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, Behavior}

object Server {

  sealed trait ServerReceivable
  final case class InitServer(remoteMasterServicePath: String) extends ServerReceivable
  final case class RegisteredServer(masterService: ActorRef[MasterServiceReceivable]) extends ServerReceivable
  final case class Query(objId: Int, from: ActorRef[ClientReceivable]) extends ServerReceivable
  final case class Update(objId: Int, newObj: String, from: ActorRef[ClientReceivable]) extends ServerReceivable

  def apply(): Behavior[ServerReceivable] = Behaviors.receive {
    (context, message) =>
      message match {
        case InitServer(remoteMasterServicePath) =>
          val masterService = context.toClassic.actorSelection(remoteMasterServicePath)
          masterService ! RegisterServer(context.self)
          context.log.info("Server: registering server at master service.")
          Behaviors.same
        case RegisteredServer(masterService) =>
          context.log.info("Server: server is registered at {}.", masterService.path)
          Behaviors.same
        case Query(objId, from) =>
          context.log.info("Server: sending a query response for objId {} = {}.", objId, "Apple")
          from ! QueryResponse(objId, "Apple")
          Behaviors.stopped
        case Update(objId, newObj, from) =>
          context.log.info("Server: sending a update response for objId {} = {}.", objId, newObj)
          from ! UpdateResponse(objId)
          Behaviors.stopped
      }
  }

}
