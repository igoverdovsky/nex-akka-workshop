package com.traiana.nagger

/**
  * Created by irenag on 2/5/18.
  */
import akka.actor.{ActorSystem, Props}
import com.google.protobuf.empty.Empty
import com.traiana.kit.boot.grpc.GrpcService
import com.traiana.nagger.spb._
import akka.pattern.ask
import akka.util.Timeout
import io.grpc.stub.StreamObserver
import io.grpc.{BindableService, ServerServiceDefinition}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
@GrpcService
class NaggerService(@Autowired applicationContext: ApplicationContext) extends NaggerGrpc.Nagger with BindableService {

  val system   = ActorSystem("NaggerCluster")
  val apiActor = system.actorOf(Props[ApiActor], "apiactor")


  implicit val timeout = Timeout(5 second)
  override def bindService(): ServerServiceDefinition = {
    NaggerGrpc.bindService(this, ExecutionContext.global)
  }

  def register(request: RegisterRequest): Future[LoginRegisterResponse] = {
    println(s"Start register ${request.username}")
    (apiActor ? request).mapTo[LoginRegisterResponse]
  }

  def login(request: LoginRequest): Future[LoginRegisterResponse] = {
    println(s"Start login ${request.username}")
    (apiActor ? request).mapTo[LoginRegisterResponse]
  }
  def joinLeave(request: JoinLeaveRequest): Future[Empty] = {
    println(s"Start joinLeave to channel ${request.channel}")
    (apiActor ? request).mapTo[Empty]
  }
  def sendMessage(request: MessageRequest): Future[Empty] = {
    println(s"Start send message to ${request.channel}")
    (apiActor ? request).mapTo[Empty]

  }

  def listen(request: ListenRequest, responseObserver: StreamObserver[ListenEvent]): Unit = {

    apiActor ! StartListen(request, responseObserver)
  }

}
