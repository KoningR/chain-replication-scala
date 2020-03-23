package actors

import actors.MasterService.RequestChainInfo
import actors.Server.{Query, ServerReceivable, Update}
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import communication.JsonSerializable

object Client {

    sealed trait ClientReceivable extends JsonSerializable
    final case class InitClient(remoteMasterServicePath: String) extends ClientReceivable
    final case class ChainInfoResponse(head: ActorRef[ServerReceivable], tail: ActorRef[ServerReceivable]) extends ClientReceivable
    final case class QueryResponse(objId: Int, queryResult: String) extends ClientReceivable
    final case class UpdateResponse(objId: Int, newValue: String) extends ClientReceivable
    final case class CallQuery(objId: Int, options: Option[List[String]]) extends ClientReceivable
    final case class CallUpdate(objId: Int, newObj: String, options: Option[List[String]]) extends ClientReceivable

    private var head: ActorRef[ServerReceivable] = _
    private var tail: ActorRef[ServerReceivable] = _

    def apply(): Behavior[ClientReceivable] = Behaviors.receive {
        (context, message) => {
            message match {
                case InitClient(remoteMasterServicePath) => initClient(context, message, remoteMasterServicePath)
                case ChainInfoResponse(head, tail) => chainInfoResponse(context, message, head, tail)
                case QueryResponse(objId, queryResult) => queryResponse(context, message, objId, queryResult)
                case UpdateResponse(objId, newValue) => updateResponse(context, message, objId, newValue)
                case CallQuery(objId, options) => callQuery(context, message, objId, options)
                case CallUpdate(objId, newObj, options) => callUpdate(context, message, objId, newObj, options)
            }
        }
    }

    def initClient(context: ActorContext[ClientReceivable], message: ClientReceivable, remoteMasterServicePath: String): Behavior[ClientReceivable] = {
        val masterService = context.toClassic.actorSelection(remoteMasterServicePath)
        masterService ! RequestChainInfo(context.self)

        context.log.info("Client: will try to get the chain's head and tail from the masterService {}.", masterService.pathString)
        Behaviors.same
    }

    def chainInfoResponse(context: ActorContext[ClientReceivable], message: ClientReceivable, head: ActorRef[ServerReceivable], tail: ActorRef[ServerReceivable]): Behavior[ClientReceivable] = {
        this.head = head
        this.tail = tail
        println(s"Head I have right now is: ${head.path.name}")
        println(s"Tail I have right now is: ${tail.path.name}")

        context.log.info("Client: received a ChainInfoResponse, head: {}, tail: {}", head.path, tail.path)
        Behaviors.same
    }

    def queryResponse(context: ActorContext[ClientReceivable], message: ClientReceivable, objId: Int, queryResult: String): Behavior[ClientReceivable] = {
        context.log.info("Client: received a QueryResponse for objId {} = {}", objId, queryResult)
        Behaviors.same
    }

    def updateResponse(context: ActorContext[ClientReceivable], message: ClientReceivable, objId: Int, newValue: String): Behavior[ClientReceivable] = {
        context.log.info("Client: received a UpdateResponse for objId {}, new value is {}", objId, newValue)
        Behaviors.same
    }

    def callQuery(context: ActorContext[ClientReceivable], message: ClientReceivable,
                  objId: Int, options: Option[List[String]]): Behavior[ClientReceivable] = {
        println(s"Sending a query request to head: ${this.head.path.name}")
        println(s"Head I have right now is: ${head.path.name}")
        println(s"Tail I have right now is: ${tail.path.name}")
        this.head ! Query(objId, options, context.self)
        Behaviors.same
    }

    def callUpdate(context: ActorContext[ClientReceivable], message: ClientReceivable,
                   objId: Int, newObj: String, options: Option[List[String]]): Behavior[ClientReceivable] = {
        println(s"Sending a update request to tail: ${this.tail.path.name}")
        println(s"Head I have right now is: ${head.path.name}")
        println(s"Tail I have right now is: ${tail.path.name}")
        this.tail ! Update(objId, newObj, options, context.self)
        Behaviors.same
    }
}
