import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.{DistributedData, Key, ORMultiMap, ORMultiMapKey}
import akka.cluster.ddata.Replicator._

import scala.concurrent.duration._

object Authenticator {
  def props = Props(new Authenticator)
  final case class Register(user: UserInfo)
  final case class LogIn(user: UserInfo)
  private case object GetUsersForVerification
  private case object GetUsers
  case object GetData
  final case class UserInfo(id: String, fullName: String, password: String)
  val writeMajority = WriteMajority(5.seconds)
  val readMajority = ReadMajority(5.seconds)
}

class Authenticator extends Actor {

  import Authenticator._

  val replicator = DistributedData(context.system).replicator
  val RegisteredUsersKey = ORMultiMapKey[ActorRef, UserInfo]("logged-users")
  implicit val cluster = Cluster(context.system)

  override def receive = receiveRegister

  def receiveRegister: Receive = {
    case Register(userInfo: UserInfo) =>
      val sdr = sender()
      val update = Update(RegisteredUsersKey, ORMultiMap.empty[ActorRef, UserInfo], writeMajority,
                          Some(sdr))(data => data.addBinding(sdr, userInfo))
      replicator ! update
    case _: UpdateResponse[ORMultiMap[ActorRef, UserInfo]] =>
    case GetUsersForVerification =>
      replicator ! Get(RegisteredUsersKey, readMajority, Some(GetUsersForVerification))
    case getUsersForVerification @ GetSuccess(RegisteredUsersKey, Some(GetUsersForVerification)) =>

  }


}
