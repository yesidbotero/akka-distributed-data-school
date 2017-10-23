import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ddata._
import akka.cluster.ddata.Replicator._

import scala.concurrent.duration._

object Authenticator {
  def props = Props(new Authenticator)

  final case class Register(user: UserInfo)

  case object GetUsers

  case object GetCounterByNodoFromActor

  case object GetCounterByNodoFromCRDT

  case object GetLastUpdater

  final case class UserInfo(id: String, fullName: String, password: String)

  val writeMajority = WriteMajority(5.seconds)
  val readMajority = ReadMajority(5.seconds)
}

class Authenticator extends Actor {

  import Authenticator._

  private val replicator = DistributedData(context.system).replicator

  private val RegisteredUsersKey: ORMultiMapKey[ActorRef, UserInfo] = ORMultiMapKey[ActorRef, UserInfo]("logged-users")
  private val CounterUpdateMessagesByNodoKey = PNCounterMapKey[ActorRef]("Counter-UMBN-key")
  private val LastUpdaterKey = LWWRegisterKey[ActorRef]("last-updater-key")

  implicit val cluster = Cluster(context.system)

  replicator ! Subscribe(CounterUpdateMessagesByNodoKey, self)
  replicator ! Subscribe(RegisteredUsersKey, self)

  private var registeredUsers = Map.empty[ActorRef, Set[UserInfo]]
  private var counterUpdateMessagesByNodo = Map.empty[ActorRef, BigInt]

  override def receive = receiveRegister
    .orElse[Any, Unit](receiveGetUsers)
    .orElse[Any, Unit](receiveUpdatesUsers)
    .orElse[Any, Unit](receiveUpdateCounter)
    .orElse[Any, Unit](receiveLastUpdater)

  private def receiveRegister: Receive = {
    case Register(userInfo: UserInfo) if !registeredUsers.exists(_._2.exists(_.id == userInfo.id)) =>
      val sdr = sender()
      sendUpdateMessageRegisteredUser(userInfo, sdr)
      sendUpdateMessageCounter(sdr)
      sendUpdateMessageLastUpdater(sdr)
      //Para hacer que la data de replique inmediatamente
      replicator ! FlushChanges
    case Register(_) => Unit
  }

  private def receiveGetUsers: Receive = {
    case GetUsers =>
      replicator ! Get(RegisteredUsersKey, readMajority, Some(sender()))
    case getUsers@GetSuccess(RegisteredUsersKey, Some(replyTo: ActorRef)) =>
      replyTo ! getUsers.get(RegisteredUsersKey).entries
    case GetFailure(RegisteredUsersKey, Some(replyTo: ActorRef)) => Unit
    case NotFound(RegisteredUsersKey, Some(replyTo: ActorRef)) => Unit
  }

  private def receiveUpdatesUsers: Receive = {
    case changed@Changed(RegisteredUsersKey) =>
      registeredUsers = changed.get(RegisteredUsersKey).entries
  }

  private def receiveUpdateCounter: Receive = {
    case changed@Changed(CounterUpdateMessagesByNodoKey) =>
      counterUpdateMessagesByNodo = changed.get(CounterUpdateMessagesByNodoKey).entries
    case GetCounterByNodoFromActor =>
      sender() ! counterUpdateMessagesByNodo
    case GetCounterByNodoFromCRDT =>
      replicator ! Get(CounterUpdateMessagesByNodoKey, readMajority, Some(sender()))
    case data@GetSuccess(CounterUpdateMessagesByNodoKey, Some(replyTo: ActorRef)) =>
      replyTo ! data.get(CounterUpdateMessagesByNodoKey).entries
    case GetFailure(RegisteredUsersKey, Some(replyTo: ActorRef)) => Unit
    case NotFound(RegisteredUsersKey, Some(replyTo: ActorRef)) => Unit
  }

  private def receiveLastUpdater: Receive = {
    case GetLastUpdater =>
      replicator ! Get(LastUpdaterKey, readMajority, Some(sender()))
    case data @ GetSuccess(LastUpdaterKey, Some(replyTo: ActorRef)) =>
      val response: ActorRef = data.get(LastUpdaterKey).value
      replyTo ! response
    case f @ GetFailure(LastUpdaterKey, Some(replyTo: ActorRef)) =>
      replyTo ! None
    case nf @ NotFound(LastUpdaterKey, Some(replyTo: ActorRef)) =>
      replyTo ! None
  }

  private def sendUpdateMessageRegisteredUser(userInfo: UserInfo, sdr: ActorRef): Unit = {
    val update = Update(RegisteredUsersKey, ORMultiMap.empty[ActorRef, UserInfo],
      writeMajority)(data => data.addBinding(sdr, userInfo))
    replicator ! update
  }

  private def sendUpdateMessageCounter(sdr: ActorRef): Unit = {
    val update = Update(CounterUpdateMessagesByNodoKey, PNCounterMap.empty[ActorRef],
      writeMajority)(data => data.increment(sdr, 1))
    replicator ! update
  }

  private def sendUpdateMessageLastUpdater(snd: ActorRef): Unit = {
    val update = Update(LastUpdaterKey, LWWRegister(snd),
      writeMajority)(data => data.withValue(snd))
    replicator ! update
  }

}
