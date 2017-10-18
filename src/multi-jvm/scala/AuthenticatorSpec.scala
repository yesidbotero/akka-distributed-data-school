import akka.actor.ActorRef
import akka.cluster.Cluster
import akka.cluster.ddata.DistributedData
import akka.cluster.ddata.Replicator.{GetReplicaCount, ReplicaCount}
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

object AuthenticatorSpec extends MultiNodeConfig {
  val node1 = role("node1")
  val node2 = role("node2")

  commonConfig(ConfigFactory.parseString(
    """
    akka.loglevel = INFO
    akka.actor.provider = "cluster"
    akka.log-dead-letters-during-shutdown = off
    """))
}

class AuthenticatorSpecMultiJvmNode1 extends AuthenticatorSpec

class AuthenticatorSpecMultiJvmNode2 extends AuthenticatorSpec

class AuthenticatorSpec extends MultiNodeSpec(AuthenticatorSpec) with STMultiNodeSpec with ImplicitSender {

  import AuthenticatorSpec._
  import Authenticator._

  override def initialParticipants: Int = roles.size

  val cluster = Cluster(system)
  val authenticator = system.actorOf(Authenticator.props)

  "Un Authenticator" must {
    "unirse a cluster" in within(20.seconds) {
      runOn(node1, node2) {
        cluster join node(node1).address
      }
      awaitAssert {
        DistributedData(system).replicator ! GetReplicaCount
        expectMsg(ReplicaCount(roles.size))
      }

      enterBarrier("after-1")
    }

    "Agregar datos a un CRDT" in within(10.seconds) {
      runOn(node1) {
        authenticator ! UserInfo("1035873906", "Yesid Botero", "zzc123")
      }

      awaitAssert {
        authenticator ! GetData
        val data = expectMsgType[Map[ActorRef, Set[UserInfo]]]
        data.head._2.head.id should be("1035873906")
      }
    }
  }
}
