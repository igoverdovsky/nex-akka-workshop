package com.traiana.nagger

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import com.traiana.nagger.spb._
import akka.pattern.{ask, pipe}
import com.traiana.nagger.spb.LoginRegisterResponse.Response

import scala.concurrent.duration._

/**
  * Created by irenag on 2/5/18.
  */
object LoginActor {
  def props(udActor: ActorRef): Props = Props(new LoginActor(udActor))

}

class LoginActor(register: ActorRef) extends Actor with ActorLogging {

  import context.dispatcher
  implicit val timeout = Timeout(10 second)
  val tokenMap: scala.collection.mutable.Map[String, String] =
    scala.collection.mutable.Map.empty[String, String]

  override def receive = {
    case request: LoginRequest => {
      log.info(s"Got LoginRequest from ${request.username}")
      val origSender = sender()
      (register ? request).mapTo[NickName].map(response => if (response.nick.isEmpty)
        origSender ! LoginRegisterResponse(Response.Failure(LoginFailure("Login failed"))) else
        (self ? LoginInternal(RegisterRequest(request.username, request.password, response.nick))).mapTo[LoginRegisterResponse].pipeTo(origSender)
      )
    }

    case request: LoginInternal => {
      log.info(s"LoginActor ------> Got  LoginInternal for ${request.reg.username}")
      val token: String = generateToken(request.reg.nickname)
      tokenMap.+=(token -> request.reg.nickname)
      sender() ! LoginRegisterResponse(Response.Success(LoginSuccess(token)))
    }
/*    case client : NickName => {
      val token: String = generateToken(client.nick)
      tokenMap.+=(token -> client.nick)
      sender() ! LoginRegisterResponse(Response.Success(LoginSuccess(token)))
    }*/
    case req: RequestToken => {
      log.info(s"LoginActor ------> Got  RequestToken for ${req.token}")
      log.info(s"LoginActor ------> tokenMaps contain : ${tokenMap}")
      tokenMap.get(req.token) match {
        case Some(value) => sender() ! NickName(value)
        case None        => sender() ! NickName("")
      }
    }

  }

  def generateToken(nick: String): String =
    nick.concat(UUID.randomUUID().toString)

}
