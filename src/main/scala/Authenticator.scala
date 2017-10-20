import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ddata._
import akka.cluster.ddata.Replicator._

import scala.concurrent.duration._

object Authenticator {
  def props = Props(new Authenticator)

  final case class Register(user: UserInfo)

  case object GetUsers

  final case class UserInfo(id: String, fullName: String, password: String)

  val writeMajority = WriteMajority(5.seconds)
  val readMajority = ReadMajority(5.seconds)
}

class Authenticator extends Actor {

  import Authenticator._

  val replicator = DistributedData(context.system).replicator
  val RegisteredUsersKey: ORMultiMapKey[ActorRef, UserInfo] = ORMultiMapKey[ActorRef, UserInfo]("logged-users")
  implicit val cluster = Cluster(context.system)
  replicator ! Subscribe(RegisteredUsersKey, self)
  var registeredUsers = Map.empty[ActorRef, Set[UserInfo]]

  override def receive = receiveRegister
    .orElse[Any, Unit](receiveGetUsers)
    .orElse[Any, Unit](receiveUpdatesUsers)

  def receiveRegister: Receive = {

    case Register(userInfo: UserInfo) if !registeredUsers.exists(_._2.exists(_.id == userInfo.id)) =>
      sendUpdateMessage(userInfo, sender())
    case Register(_) =>
      Unit

    case _: UpdateResponse[ORMultiMap[ActorRef, UserInfo]] =>
  }

  def receiveGetUsers: Receive = {
    case GetUsers =>
      replicator ! Get(RegisteredUsersKey, readMajority, Some(sender()))
    case getUsers@GetSuccess(RegisteredUsersKey, Some(sdr: ActorRef)) =>
      sdr ! getUsers.get(RegisteredUsersKey).entries
    case _: GetResponse[_] =>
  }

  def receiveUpdatesUsers: Receive = {
    case changed@Changed(RegisteredUsersKey) =>
      registeredUsers = changed.get(RegisteredUsersKey).entries
  }

  def sendUpdateMessage(userInfo: UserInfo, sdr: ActorRef) = {
    val update = Update(RegisteredUsersKey, ORMultiMap.empty[ActorRef, UserInfo], writeMajority,
      Some(sdr))(data => data.addBinding(sdr, userInfo))
    replicator ! update
    replicator ! FlushChanges
  }

}
