import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.{DistributedData, ORSet, ORSetKey}

import scala.concurrent.duration._

object Greeter {
  import akka.cluster.ddata.Replicator._

  def props = Props(new Greeter)
  case class Greet(message: String)
  case object GetData
  case object DeleteData
  case object DeleteDataSuccess
  case object NoData
  case class DeleteGreeting(greet: Greet)

  private val readMajority = ReadMajority(3.seconds)
  private val writeMajority = WriteMajority(3.seconds)
}

class Greeter extends Actor {
  import Greeter._
  import akka.cluster.ddata.Replicator._

  //Se obtiene el ActorRef del Replicator asociado al Cluster
  val replicator = DistributedData(context.system).replicator
  //Se define la llave con la que se accederá a los datos
  val DataKey = ORSetKey[String]("data-key")
  implicit val cluster = Cluster(context.system)
  //Si se quiere que el actor recibe un mensaje cada vez que una actualización sea realizada
  //replicator ! Subscribe(DataKey, self)

  override def receive = receiveGreet
    .orElse[Any, Unit](receiveGetData)
    .orElse[Any, Unit](receiveDeleteData)
    .orElse[Any, Unit](receiveDeleteGreeting)
    .orElse[Any, Unit](receiveOther)


  def receiveGreet: Receive = {
    case Greet(greet: String) =>
      replicator ! Update(DataKey, ORSet.empty[String], writeMajority, Some(sender()))(_ + greet)
    case _: UpdateResponse[_] =>
  }

  def receiveGetData: Receive = {
    case GetData =>
      replicator ! Get(DataKey, readMajority, Some(sender()))
    case g @ GetSuccess(DataKey, Some(originalSender: ActorRef)) =>
      val saludos: Set[String] = g.get(DataKey).elements
      originalSender ! saludos
    case _: GetFailure[_] =>
  }

  def receiveDeleteData: Receive = {
    case DeleteData =>
      replicator ! Delete(DataKey, writeMajority, Some(sender()))
    case d @ DeleteSuccess(_, Some(originalSender: ActorRef)) =>
      originalSender ! DeleteDataSuccess
    case dataDeleted @ DataDeleted(_, Some(originalSender: ActorRef)) =>
      originalSender ! NoData
  }

  def receiveDeleteGreeting: Receive = {
    case DeleteGreeting(greet: Greet) =>
      replicator ! Update(DataKey, ORSet.empty[String], writeMajority, Some(sender()))(_ - greet.message)
  }

  def receiveOther: Receive = {
    case _: UpdateSuccess[_] | _: UpdateTimeout[_] =>
    case e: UpdateFailure[_]                       => throw new IllegalStateException("Unexpected failure: " + e)
  }


}


