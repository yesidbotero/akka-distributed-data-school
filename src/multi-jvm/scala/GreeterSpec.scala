import akka.actor.ActorRef
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
      runOn(nodo1, nodo2) {
        cluster join node(nodo1).address
      }
      awaitAssert {
        DistributedData(system).replicator ! GetReplicaCount
        expectMsg(ReplicaCount(roles.size))
      }

      enterBarrier("after-1")
    }

    "Subscribirse al log de remitentes de mensajes" in within(10.seconds) {
      runOn(nodo1) {
        greeter ! Greet("Hola")
      }
      awaitAssert {
        greeter ! GetLogFromActor
        val log = expectMsgType[Set[ActorRef]]
        log.size should be(1)
      }
    }


    "Desuscribirse del log de remitentes de mensajes" in within(10.seconds) {
      runOn(nodo1) {
        greeter ! UnSubscribeToLog
        greeter ! Greet("Ni Hao")
      }

      awaitAssert {
        greeter ! GetLogFromActor
        val log = expectMsgType[Set[ActorRef]]
        //Deberìa seguir siendo 1 el número de ActorRef en el log
        log.size should be(1)
      }
    }

    "agregar y eliminar concurrentemente un elemento, predominando la agregacion" in within(20.seconds) {
      runOn(nodo2) {
        greeter ! Greet("Hola!")
      }

      runOn(nodo1) {
        greeter ! DeleteGreeting(Greet("Hola!"))
      }

      awaitAssert {
        greeter ! GetData
        //el elemento "Hola!" continua en la data
        expectMsg(Set("Hola", "Hola!", "Ni Hao"))
      }
    }

    "Puede eliminar todos los elementos" in within(20.seconds) {
      runOn(nodo2) {
        greeter ! DeleteData
      }
      awaitAssert {
        greeter ! GetData
        expectMsg(NoData)
      }
    }

  }
}
