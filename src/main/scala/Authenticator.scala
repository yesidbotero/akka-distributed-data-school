import akka.actor.{Actor, Props}
import akka.cluster.ddata.Replicator.{ReadMajority, WriteMajority}

import scala.concurrent.duration._

object Authenticator {
  def props = Props(new Authenticator)

  final case class newEntry(id: String, password: String)
  val writeMajority = WriteMajority(4.seconds)
  val readMajority = ReadMajority(4.seconds)
}

class Authenticator extends Actor {

  import Authenticator._

  override def receive = {
    case newEntry(id: String, password: String) =>
  }
}
