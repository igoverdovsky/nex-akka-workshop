package com.traiana.nagger

import akka.pattern.{ask, pipe}

import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.persistence.{PersistentActor, RecoveryCompleted}
import akka.util.Timeout
import com.google.protobuf.empty.Empty
import com.traiana.nagger.spb._
import com.traiana.nagger.spb.LoginRegisterResponse.Response

import scala.concurrent.ExecutionContext

/**
  * Created by irenag on 2/5/18.
  */
class UserDetailsActor extends Actor with ActorLogging with PersistentActor {

  import context.dispatcher
  implicit val timeout = Timeout(10 seconds)
  val userMap: scala.collection.mutable.Map[String, (String, String)] =
    scala.collection.mutable.Map.empty[String, (String, String)]
  val persistenceId: String = "user-details-1"
  var apiActorsSet : Set[ActorRef] = Set.empty[ActorRef]

  def updateState(rr: UserDetailsEvent) = userMap.+=(rr.user -> (rr.password, rr.nick))

  override def receiveCommand = {

    case request : ApiActorInfo =>
    {
      log.info(s"Add one more apiActor : ${request.apiActor.path}")
      apiActorsSet = apiActorsSet.+(request.apiActor)}

    case request : Notify =>
      {
        apiActorsSet.foreach(actor => {
          log.info(s"set notify message to ${actor.path}")
          actor ! Notify(request.msg, request.set)})
      }
    case rr: Registration => {
      //todo check user if already exists
      userMap.get(rr.req.username) match {
        case Some(value) => {
          if ((value._1.equals(rr.req.password) && (value._2.equals(rr.req.nickname)))) {
            log.info("The user already registered")
            sender() ! LoginRegisterResponse(Response.Failure(LoginFailure(s"The user ${rr.req.username} already exists")))
          }
          else{
            log.info("User already exists")
            sender() ! LoginRegisterResponse(Response.Failure(LoginFailure(s"The user ${rr.req.username} already exists, register info is wrong")))

          }
        }
        case None => {
          log.info("Got RegisterRequest from : " + rr.req.username)
          val origSender = sender()
          val event = UserDetailsEvent(rr.req.username, rr.req.password, rr.req.nickname)
          updateState(event)
          persist(event) {
            log.info("Going to update state")
            event =>
              log.info("Call LoginActor from UserDetailsActor during registration flow ")
              (rr.login ? LoginInternal(rr.req)).mapTo[LoginRegisterResponse].pipeTo(origSender)
          }
        }
      }
    }
    case event: UserDetailsEvent =>  {
      log.info("Got UserDetailsEvent ")
      updateState(event)
      sender() ! Empty()
    }

    case loginValidation: LoginRequest => {
      log.info(s"UserDetailsActor got LoginRequest for ${loginValidation.username}")
      userMap.get(loginValidation.username) match {
        case Some(value) => {
          if (value._1.equals(loginValidation.password)) {
            log.info(s"User ${loginValidation.username} is valid")
            sender() ! NickName(value._2)
          } else {
            log.info(s"The password of ${loginValidation.username} is not passed validation")
            sender() ! NickName("")
          }
        }
        case None => {
          log.info(s"One of username/password of ${loginValidation.username} is not passed validation")
          sender() ! NickName("")
        }
      }

    }

  }

  override def receiveRecover = {
    case event: UserDetailsEvent =>  updateState(event)
    case RecoveryCompleted       => {
      log.info(s"User in memory contains : ${userMap.size} members")
      log.info(s"Users in memory : ${userMap.keys} ")
      log.info("Recovery for user registration completed!")
    }
  }

}
