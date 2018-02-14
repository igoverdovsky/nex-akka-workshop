package com.traiana.nagger

import akka.pattern.{ask, pipe}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}

import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.persistence.{Persistence, PersistentActor, RecoveryCompleted}
import akka.util.Timeout
import com.google.protobuf.empty.Empty
import com.traiana.nagger.ChannelManagerActor.JoinLeaveEvent
import com.traiana.nagger.spb.{JoinLeaveRequest, ListenEvent, MessageRequest}

import scala.concurrent.duration._

/**
  * Created by irenag on 2/5/18.
  */
object ChannelManagerActor {
  def props(id: String): Props = Props(new ChannelManagerActor)

  sealed trait ChannelManagerEvent
  case class JoinLeaveEvent(channel: String, nick: String, joinLeave: Boolean) extends ChannelManagerEvent


}

class ChannelManagerActor extends Actor with PersistentActor with ActorLogging {

 // val channels: scala.collection.mutable.Map[String, ActorRef] = scala.collection.mutable.Map.empty[String, ActorRef]
  implicit val es                                              = scala.concurrent.ExecutionContext.global
  override def persistenceId                                   = "channel-manager-13"
  implicit val timeout                                         = Timeout(5 second)




  val channelRegion: ActorRef = ClusterSharding(context.system).start(
    typeName = ChannelActor.shardName,
    entityProps = ChannelActor.props(),
    settings = ClusterShardingSettings(context.system),
    extractEntityId = ChannelActor.extractEntityId,
    extractShardId = ChannelActor.extractShardId)


  override def receiveRecover = {


    case req: JoinLeaveEvent => {

      channelRegion ! EntityEnvelope(req.channel, req)

    }
    case RecoveryCompleted       => {
     // log.info(s"Got channels : ${channels.keys}")
      log.info("ChannelManager Recovery completed!")
    }
  }

  override def receiveCommand = {



    case event: JoinLeaveEvent => {
      persist(event) { event =>
        log.info("Persisted")
      }
      sender() ! Empty()

    }
    case request: ChannelRequest => {
      log.info("ChannelManagerActor got JoinLeaveRequest")
     // val channelActor: Option[ActorRef] = channels.get(request.req.channel)
      val origSender = sender()
    //  channelActor match {
      //    case Some(actor) => {

          if (!request.req.joinNotLeave) {
            log.info("Going to leave channel : " + request.req.channel)
            (channelRegion ? EntityEnvelope(request.req.channel,LeaveRequest(request.req, request.nick)))
              .map(_ => (self ? (JoinLeaveEvent(request.req.channel, request.nick, false))))
              .mapTo[Empty].pipeTo(origSender)
          } else {
            log.info(s"Nick ${request.nick} join to channel ${request.req.channel} ")
            (channelRegion ? EntityEnvelope(request.req.channel,Client(request.nick)))
              .mapTo[ChannelMessageHistory].map(list => (origSender ? ChannelMessageHistoryForSpecificClient(request.nick, list)))
              .map(_ => (self ? JoinLeaveEvent(request.req.channel, request.nick, true)))
              .mapTo[Empty].pipeTo(origSender)
          }
        }
      case req: AddChannel => {

      (channelRegion ? EntityEnvelope(req.channel,Client(req.nick))).mapTo[Empty].pipeTo(sender())
    }
    case msg: MessageRequest => {
      val origSender = sender() //apiactor
   //   log.info(s"Channels contains : ${channels.keys}")
      log.info(s"Channel manager receive MessageRequest to channel : ${msg.channel}")
    //  channels.get(msg.channel) match {
    //    case Some(value: ActorRef) => {
          log.info(s"Going to run Channel actor for channel ${msg.channel}")
          (channelRegion ? EntityEnvelope(msg.channel, msg)).mapTo[SetClients].pipeTo(origSender)

        }

    case msg: ChannelMessage => {
      val origSender = sender() //apiactor
      //log.info(s"Channels contains : ${channels.keys}")
      log.info(s"Channel manager receive ChannelMessage to channel : ${msg.msg.channel}")
          log.info(s"Going to run Channel actor for channel ${msg.msg.channel}")
      var set : SetClients = SetClients(Set.empty[String])
          (channelRegion ? EntityEnvelope( msg.msg.channel, msg)).mapTo[SetClients].map(clients => {
            set = clients
            set}).mapTo[SetClients].pipeTo(origSender)


/*             apiActorsSet.foreach(actor => {
               log.info(s"set notify message to ${actor.path}")
                actor ! Notify(msg, set)})*/

    }

  }

}
