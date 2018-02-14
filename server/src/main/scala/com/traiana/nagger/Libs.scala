package com.traiana.nagger

import akka.actor.ActorRef
import com.traiana.nagger.spb._
import io.grpc.stub.StreamObserver

/**
  * Created by irenag on 2/5/18.
  */
case object Libs
case object End
case class ApiActorInfo(apiActor : ActorRef)
case class Notify(msg : ChannelMessage, set : SetClients)
final case class Get(counterId: String)
final case class EntityEnvelope(id: String, payload: Any)
case class Client(nick: String)
case class StartListen(event: ListenRequest, stream: StreamObserver[ListenEvent])
case class LoginInternal(reg: RegisterRequest)
case class RequestToken(token: String)
case class ResponseTokenNick(token: String, nick: String)
case class NickName(nick: String)
case class AddChannel(channel: String, nick: String)
case class Registration(req: RegisterRequest, login: ActorRef)
case class JoinRequest(req: JoinLeaveRequest, nick: String)
case class LeaveRequest(req: JoinLeaveRequest, nick: String)
case class ChannelRequest(req: JoinLeaveRequest, nick: String)

case class UserDetailsEvent(user: String, password: String, nick: String)
case class ChannelMessageHistory(channel : String, history : List[(String, String)])
case class ChannelMessageHistoryForSpecificClient(client : String, history : ChannelMessageHistory)
case class SetClients(clients: Set[String])
case class NickToStream(nick: String, stream: StreamObserver[ListenEvent], sender: ActorRef)
case class ChannelMessage(msg : MessageRequest, nick: String)