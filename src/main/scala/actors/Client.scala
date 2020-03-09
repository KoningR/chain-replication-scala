package actors

import actors.MasterService.{MasterServiceReceivable, PingMasterService, RequestChainInfo}
import actors.Server.{ServerReceivable, Update}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, Behavior}

object Client {

  sealed trait ClientReceivable
  final case class InitClient(remoteMasterServicePath: String) extends ClientReceivable
  final case class PongClient(masterService: ActorRef[MasterServiceReceivable]) extends ClientReceivable
  final case class ChainInfoResponse(head: ActorRef[ServerReceivable], tail: ActorRef[ServerReceivable]) extends ClientReceivable
  final case class QueryResponse(objId: Int, obj: String) extends ClientReceivable
  final case class UpdateResponse(objId: Int) extends ClientReceivable

  var head: ActorRef[ServerReceivable] = _
  var tail: ActorRef[ServerReceivable] = _

  def apply(): Behavior[ClientReceivable] = Behaviors.receive {
    (context, message) =>
      message match {
        case InitClient(remoteMasterServicePath) =>
          val masterService = context.toClassic.actorSelection(remoteMasterServicePath)
          context.log.info("Client: will try to discover masterService {} by pinging it.", masterService.pathString)
          masterService ! PingMasterService(context.self)
          Behaviors.same
        case PongClient(masterService) =>
          context.log.info("Client: has discovered server! Requesting chain info.")
          masterService ! RequestChainInfo(context.self)
          Behaviors.same
        case ChainInfoResponse(head, tail) =>
          context.log.info("Client: received a ChainInfoResponse, head: {}, tail: {}", head.path, tail.path)
          this.head = head
          this.tail = tail
          this.head ! Update(1, "New object", context.self)
          Behaviors.same
        case QueryResponse(objId, obj) =>
          context.log.info("Client: received a QueryResponse for objId {} = {}", objId, obj)
          Behaviors.same
        case UpdateResponse(objId) =>
          context.log.info("Client: received a UpdateResponse for objId {}", objId)
          Behaviors.same
      }
  }

}
