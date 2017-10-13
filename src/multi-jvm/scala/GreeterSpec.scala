import akka.cluster.Cluster
import akka.cluster.ddata.DistributedData
import akka.cluster.ddata.Replicator.{GetReplicaCount, ReplicaCount}
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

object GreeterSpec extends MultiNodeConfig {
  val nodo1 = role("nodo-1")
  val nodo2 = role("nodo-2")

  commonConfig(ConfigFactory.parseString(
    """
    akka.loglevel = INFO
    akka.actor.provider = "cluster"
    akka.log-dead-letters-during-shutdown = off
    """))
}

class GreeterSpecMultiJvmNode1 extends GreeterSpec

class GreeterSpecMultiJvmNode2 extends GreeterSpec

class GreeterSpec extends MultiNodeSpec(GreeterSpec) with STMultiNodeSpec with ImplicitSender {

  import GreeterSpec._
  import Greeter._

  override def initialParticipants: Int = roles.size

  val cluster = Cluster(system)
  val greeter = system.actorOf(Greeter.props)


  "Un Greeter" must {
    "unirse a cluster" in within(20.seconds) {
      runOn(nodo1) {
        cluster join node(nodo1).address
      }
      runOn(nodo2) {
        cluster join node(nodo1).address
      }

      awaitAssert{
        DistributedData(system).replicator ! GetReplicaCount
        expectMsg(ReplicaCount(roles.size))
      }

      enterBarrier("after-1")
    }

    "enviar un saludo" in within(15.seconds) {
      runOn(nodo1) {
        greeter ! Greet("Hello")
      }

      runOn(nodo2){
        greeter ! Greet("Hola")
      }

      awaitAssert{
        greeter ! GetData
        expectMsg(Set("Hello", "Hola"))
      }
    }
  }
}
