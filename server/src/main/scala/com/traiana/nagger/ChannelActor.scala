package com.traiana.nagger

import akka.actor.SupervisorStrategy.Stop
import akka.pattern._
import akka.actor.{Actor, ActorLogging, ActorRef, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.persistence.{PersistentActor, RecoveryCompleted}
import akka.util.Timeout
import com.google.protobuf.empty.Empty
import com.traiana.nagger.ChannelActor.{ChannelEvent, ClientJoinEvent, ClientLeaveEvent, MessageEvent}
import com.traiana.nagger.spb.{JoinLeaveRequest, MessageRequest}

import scala.concurrent.duration._

/**
  * Created by irenag on 2/5/18.
  */
object ChannelActor {
  def props(): Props = Props(new ChannelActor())

  sealed trait ChannelEvent
  case class ClientJoinEvent(client: Client)  extends ChannelEvent
  case class ClientLeaveEvent(client: Client) extends ChannelEvent
  case class MessageEvent(msg : (String , String) ) extends ChannelEvent


  val extractEntityId: ShardRegion.ExtractEntityId = {
    case EntityEnvelope(id, payload) ⇒ (id, payload)
    case msg @ Get(id)               ⇒ (id, msg)
  }

  val numberOfShards = 10

  val extractShardId: ShardRegion.ExtractShardId = {
    case EntityEnvelope(id, _) ⇒ (id.length % numberOfShards).toString
    case Get(id)               ⇒ (id.length % numberOfShards).toString
    case ShardRegion.StartEntity(id) =>
      (id.length % numberOfShards).toString
  }

  val shardName: String = "ChannelActor"

}

class ChannelActor extends Actor with PersistentActor with ActorLogging {
  import ChannelActor._
  import akka.cluster.sharding.ShardRegion.Passivate
  context.setReceiveTimeout(120.seconds)

 // implicit val timeout                              = Timeout(5 second)
  import context.dispatcher
  implicit val timeout = Timeout(3 seconds)
  var clients: scala.collection.mutable.Set[String] = scala.collection.mutable.Set.empty[String]
  var messages: scala.collection.mutable.ListBuffer[(String, String)] = scala.collection.mutable.ListBuffer.empty[(String, String)]


  override def receiveRecover = {
    case event: ClientJoinEvent  => clients.+=(event.client.nick)
    case event: ClientLeaveEvent => clients.-=(event.client.nick)
    case event : MessageEvent =>   messages.+=(event.msg._1 -> event.msg._2)
    case RecoveryCompleted       => {
      log.info(s"ChannelActor Recovery flow clients : ${clients}")
      log.info(s"ChannelActor Recovery flow messages : ${messages}")
      log.info("ChannelActor Recovery completed!")
    }
  }

  override def persistenceId = s"channel-${self.path.name}"

  override def receiveCommand = {

    case newClient: Client => {
      clients.+=(newClient.nick)
      val origSender =sender()
      (self ?  ClientJoinEvent(newClient)).pipeTo(origSender)

    }
    case event: ChannelEvent => {
      persist(event) { evt =>
        log.info("Channel Event Persisted")
        sender() ! ChannelMessageHistory(self.path.name, messages.toList)
      }


    }
    case msg: MessageRequest => {
      log.info("Clients contains: " + clients.toString)
      //todo send to all from the list
      sender() ! SetClients(clients.toSet)
    }
    case msg : ChannelMessage =>
      {
        log.info("Got  ChannelMessage : " + clients.toString)
        log.info("Clients contains: " + clients.toString)
        val origSender = sender()
        messages.+=((msg.nick, msg.msg.message))
        (self ?  MessageEvent(msg.nick, msg.msg.message)).mapTo[ChannelMessageHistory].map(res => SetClients(clients.toSet)).pipeTo(origSender)
      }
    case ReceiveTimeout ⇒ context.parent ! Passivate(stopMessage = Stop)
    case Stop           ⇒ context.stop(self)

  }

}
