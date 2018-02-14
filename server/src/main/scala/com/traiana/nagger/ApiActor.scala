package com.traiana.nagger

import akka.pattern.{ask, pipe}
import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import akka.cluster.Cluster
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.util.Timeout
import com.google.protobuf.empty.Empty
import com.traiana.nagger.spb._
import io.grpc.stub.StreamObserver

import scala.concurrent.duration._
import scala.concurrent.Future

/**
  * Created by irenag on 2/5/18.
  */
class ApiActor extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

 // val regActor            = context.actorOf(Props[UserDetailsActor], "register")
 val regmanager            = context.actorOf(
    ClusterSingletonManager.props(
      singletonProps = Props(classOf[UserDetailsActor]),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(context.system)),
      name = "register")
  val regActor =  context.actorOf(
    ClusterSingletonProxy.props(
      singletonManagerPath = "/user/apiactor/register",
      settings = ClusterSingletonProxySettings(context.system)),
    name = "registerProxy")

  val loginActor          = context.actorOf(LoginActor.props(regActor), "loginer")

 /* val msgmanager            = context.actorOf(
    ClusterSingletonManager.props(
      singletonProps = Props(classOf[ChannelManagerActor]),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(context.system)),
    name = "channelmanager")
  val channelManagerActor =  context.actorOf(
    ClusterSingletonProxy.props(
      singletonManagerPath = "/user/apiactor/channelmanager",
      settings = ClusterSingletonProxySettings(context.system)),
      name = "channelmanagerProxy")*/

  log.info("registration to channel manager")
  regActor ! ApiActorInfo(self)
  val channelManagerActor = context.actorOf(Props[ChannelManagerActor], "channelmanager")

  val nickToStream: scala.collection.mutable.Map[String, StreamObserver[ListenEvent]] =
    scala.collection.mutable.Map.empty[String, StreamObserver[ListenEvent]]
  implicit val timeout = Timeout(5 second)
  import context.dispatcher
  //implicit val es      = scala.concurrent.ExecutionContext.global

  override def receive = {

    case cmd: RegisterRequest => {
      val origSender = sender()
      log.info(s"ApiActor -----> Receive RegisterRequest for user :  ${cmd.username}")
      (regActor ? Registration(cmd, loginActor)).mapTo[LoginRegisterResponse].pipeTo(origSender)
    }
    case cmd: LoginRequest => {
      log.info(s"ApiActor -----> Receive LoginRequest for user : ${cmd.username}")
      val origSender = sender()
      (loginActor ? cmd).mapTo[LoginRegisterResponse].pipeTo(origSender)
    }
    case cmd: JoinLeaveRequest => {
      log.info(s"ApiActor -----> Receive JoinLeaveRequest for channel : ${cmd.channel}")
      (loginActor ? RequestToken(cmd.token))
        .mapTo[NickName].map(name => ChannelRequest(cmd, name.nick)).pipeTo(channelManagerActor).mapTo[Empty]

    }
    case listen: StartListen => {
      log.info(s"Got StartListen request for ${listen.event.token}")
      val origSender = sender()
      (loginActor ? RequestToken(listen.event.token))
        .mapTo[NickName].map(str => NickToStream(str.nick, listen.stream, origSender)).pipeTo(self)
    }
    case request: NickToStream => {

      nickToStream.+=(request.nick -> request.stream)

      request.sender ! Empty()
    }

    case cmd: MessageRequest => {
      val origSender = sender()
      log.info(s"ApiActor -----> Receive MessageRequest for channel : ${cmd.channel}")
      (loginActor ? RequestToken(cmd.token))
        .mapTo[NickName].map(nickName => {
         log.info(s"ApiActor ----->  find the nick : ${nickName.nick}")
        val channelMsg = ChannelMessage(cmd, nickName.nick)
        (channelManagerActor ? channelMsg).mapTo[SetClients].map(result =>
          {
            log.info(s"ApiActor ----->  Got clients: ${result.clients}")
           regActor ! Notify(channelMsg, result)
          })
      }).mapTo[Empty].pipeTo(origSender)

    }
    case request : ChannelMessageHistoryForSpecificClient =>{
      log.info(s"ChannelMessageHistoryForSpecificClient , at this moment we have strams in nickToStream : ${nickToStream.keys}")
      log.info(s"ApiActor internal call for ChannelMessageHistoryForSpecificClient for client : ${request.client}")
      nickToStream.get(request.client) match {
        case Some(value) => {
          log.info(s"Going to populate history of channel ${request.history.channel}")
          log.info(s"Going to populate history  ${request.history.history}")
          request.history.history.foreach(message => {
            log.info(s"Going to create event with message : ${message._2} from user : ${message._1}")
            //todo add timestamp
            val listenEvent: ListenEvent = ListenEvent(request.history.channel, message._1, message._2)
            value.onNext(listenEvent)
            log.info(s"ChannelMessageHistoryForSpecificClient Message posted to ${request.client}")
          })
        }
        case None => {
          println("The stream could not be found")
        }
      }
    }
    case msg :  Notify =>
      {
        msg.set.clients.foreach(cl => {
          log.info("Notify Inside loop of clients")
          nickToStream.get(cl) match {
            case Some(value) => {
              //todo add timestamp
              val listenEvent: ListenEvent = ListenEvent(msg.msg.msg.channel, msg.msg.nick, msg.msg.msg.message)
              value.onNext(listenEvent)
              log.info(s"Message posted to ${cl}")
            }
            case None => {
              println("The stream could not be found")
            }
          }
        })
      }
    //  sender() ! Empty()
  }
}

/*    case cmd: MessageRequest => {
  val origSender = sender()
  log.info(s"ApiActor -----> Receive MessageRequest for channel : ${cmd.channel}")
  (channelManagerActor ? cmd)
    .mapTo[SetClients].map(result =>
      result.clients.foreach(cl => {
        log.info("Inside loop of clients")
        nickToStream.get(cl) match {
          case Some(value) => {
            (loginActor ? RequestToken(cmd.token))
              .mapTo[NickName].map(nickName => {
                val listenEvent: ListenEvent = ListenEvent(cmd.channel, nickName.nick, cmd.message)
                value.onNext(listenEvent)
                log.info(s"Message posted to ${cl}")
              })
          }
          case None => {
            println("The stream could not be found")
          }
        }
      })).mapTo[Empty].pipeTo(origSender)
}*/