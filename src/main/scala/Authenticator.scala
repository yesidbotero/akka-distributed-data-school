import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.{DistributedData, ORMultiMap, ORMultiMapKey, ReplicatedData}
import akka.cluster.ddata.Replicator._

import scala.concurrent.duration._

object Authenticator {
  def props = Props(new Authenticator)
  case object GetData
  final case class UserInfo(id: String, fullName: String, password: String)
  val writeMajority = WriteMajority(3.seconds)
  val readMajority = ReadMajority(3.seconds)
}

class Authenticator extends Actor {

  import Authenticator._

  val replicator = DistributedData(context.system).replicator
  val loggedUsersKey = ORMultiMapKey[ActorRef, UserInfo]("logged-users")
  implicit val cluster = Cluster(context.system)

  override def receive = {
    case UserInfo(id: String, fullName: String, password: String) =>
      val update = Update(loggedUsersKey, ORMultiMap.empty[ActorRef, UserInfo],
        writeMajority, Some(sender()))(data => data.addBinding(sender(), UserInfo(id, fullName, password)))
      replicator ! update
    case _:UpdateResponse[_] =>
    case GetData =>
      replicator ! Get(loggedUsersKey, readMajority, Some(sender()))
    case getDataSucces@GetSuccess(loggedUsersKey, Some(enviarA: ActorRef)) =>
      val data = getDataSucces.get(loggedUsersKey).asInstanceOf[ORMultiMap[ActorRef, UserInfo]].entries
      enviarA ! data
  }
}
