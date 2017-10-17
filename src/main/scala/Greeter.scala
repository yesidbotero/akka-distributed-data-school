import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ddata._

import scala.concurrent.duration._

object Greeter {

  import akka.cluster.ddata.Replicator._

  def props = Props(new Greeter)

  case object SubscribeTolog

  case object UnSubscribeToLog

  final case class Greet(message: String)

  case object GetData

  case object GetLogFromActor

  case object DeleteData

  case object DeleteDataSuccess

  case object NoData

  final case class DeleteGreeting(greet: Greet)

  private val readMajority = ReadMajority(3.seconds)
  private val writeMajority = WriteMajority(3.seconds)
}

class Greeter extends Actor {

  import Greeter._
  import akka.cluster.ddata.Replicator._

  //Se obtiene el ActorRef del Replicator asociado al Cluster
  val replicator = DistributedData(context.system).replicator
  //Se define la llave con la que se accederá a los datos
  val DataKeyGreetings = ORSetKey[String]("data-key")
  val DataKeyLog = GSetKey[ActorRef]("data-key-log")
  var log: Set[ActorRef] = Set.empty
  implicit val cluster = Cluster(context.system)
  //Si se quiere que el actor recibe un mensaje cada vez que una actualización sea realizada
  replicator ! Subscribe(DataKeyLog, self)

  override def receive = receiveSubscription
    .orElse[Any, Unit](receiveGreet)
    .orElse[Any, Unit](receiveGetData)
    .orElse[Any, Unit](receiveDeleteData)
    .orElse[Any, Unit](receiveDeleteGreeting)
    .orElse[Any, Unit](receiveOther)

  def receiveSubscription: Receive = {
    case SubscribeTolog =>
      replicator ! Subscribe(DataKeyLog, self)
    case UnSubscribeToLog =>
      replicator ! Unsubscribe(DataKeyLog, self)
    case change @ Changed(DataKeyLog) =>
      log = change.get(DataKeyLog).elements
    case GetLogFromActor =>
      sender() ! log
  }

  def receiveGreet: Receive = {
    case Greet(greet: String) =>
      replicator ! Update(DataKeyGreetings, ORSet.empty[String], writeMajority, Some(sender()))(_ + greet)
      replicator ! Update(DataKeyLog, GSet(), writeMajority)(_ + sender)
    case _: UpdateResponse[_] =>
  }

  def receiveGetData: Receive = {
    case GetData =>
      replicator ! Get(DataKeyGreetings, readMajority, Some(sender()))
    case g@GetSuccess(DataKeyGreetings, Some(originalSender: ActorRef)) =>
      val saludos: Set[String] = g.get(DataKeyGreetings).elements
      originalSender ! saludos
    case _: GetFailure[_] =>
  }

  def receiveDeleteData: Receive = {
    case DeleteData =>
      replicator ! Delete(DataKeyGreetings, writeMajority, Some(sender()))
    case d@DeleteSuccess(_, Some(originalSender: ActorRef)) =>
      originalSender ! DeleteDataSuccess
    case dataDeleted@DataDeleted(_, Some(originalSender: ActorRef)) =>
      originalSender ! NoData
  }

  def receiveDeleteGreeting: Receive = {
    case DeleteGreeting(greet: Greet) =>
      replicator ! Update(DataKeyGreetings, ORSet.empty[String], writeMajority, Some(sender()))(_ - greet.message)
  }

  def receiveOther: Receive = {
    case _: UpdateSuccess[_] | _: UpdateTimeout[_] =>
    case e: UpdateFailure[_] => throw new IllegalStateException("Unexpected failure: " + e)
  }


}


