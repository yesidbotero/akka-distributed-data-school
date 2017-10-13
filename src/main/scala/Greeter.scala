import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.{DistributedData, ORSet, ORSetKey}

import scala.concurrent.duration._

object Greeter {
  import akka.cluster.ddata.Replicator._

  def props = Props(new Greeter)
  case class Greet(greet: String)

  private val readMajority = ReadMajority(3.seconds)
  private val writeMajority = WriteMajority(3.seconds)
}

class Greeter extends Actor {
  import Greeter._
  import akka.cluster.ddata.Replicator._

  //Se obtiene el ActorRef del Replicator asociado al Cluster
  val replicator = DistributedData(context.system).replicator
  //Se define la llave con la que se accederá a los datos
  val DataKey = ORSetKey[String]("llave")
  implicit val cluster = Cluster(context.system)
  //Si se quiere que el actor recibe un mensaje cada vez que una actualización sea realizada
  //replicator ! Subscribe(DataKey, self)

  override def receive: Receive = {
    case Greet(greet) =>
      replicator ! Update(DataKey, ORSet.empty[String], writeMajority, Some(sender()))(_ + " ")
    case u @ UpdateSuccess(DataKey, Some(originalSender: ActorRef)) =>
      replicator ! Get(DataKey, readMajority, Some(originalSender))
    case g @ GetSuccess(DataKey, Some(originalSender: ActorRef)) =>
      val saludos = g.get(DataKey).elements
      originalSender ! saludos
  }
}


