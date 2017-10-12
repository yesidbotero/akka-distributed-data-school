import java.util.concurrent.ThreadLocalRandom

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable}
import akka.cluster.Cluster
import akka.cluster.ddata.DistributedData
import akka.cluster.ddata.ORSet
import akka.cluster.ddata.ORSetKey
import akka.cluster.ddata.Replicator
import akka.cluster.ddata.Replicator._

import scala.concurrent.duration._

object DataBot {
  private case object Tick
}

class DataBot extends Actor with ActorLogging {
  import DataBot._
  //El replicador permite interactuar con los datos
  //También se puede obtener el replicador a través del método Props, haciendo Replicator.Props
  val replicator: ActorRef = DistributedData(context.system).replicator
  implicit val node = Cluster(context.system)

  import context.dispatcher
  // se programa para que se envie un mensaje cada 5 segundo al ActorRef
  val tickTask: Cancellable = context.system.scheduler.schedule(5.seconds, 5.seconds, self, Tick)
  /*
    DataKey es la llave con el tipo de valor de los datos, es decir se proporciona el valor de la llave y el tipo de dato
    de la informiación asociada a la llave
  */
  val DataKey = ORSetKey[String]("key")

  /* Se le dice al replicator que subscriba al actor DataBot a cualquier cambio que se haga en el OR-Set, esto se hace
   a travès de de ORSetKey
  */
  replicator ! Subscribe(DataKey, self)

  def receive = {
    case Tick =>
      //Se genera el mensaje
      val s: String = ThreadLocalRandom.current().nextInt(97, 123).toChar.toString
      //Se decide si se va a agregar o a remover elementos
      if (ThreadLocalRandom.current().nextBoolean()) {
        // add
        log.info("Adding: {}", s)
        /*
          Con update se le pasa el valor asociado a la llave, si no hay ningún valor asociado a la llave, entonces
          se le pasa el valor proporcionado como inicial a la funcion modify
          Seguidamente se el indicael niver de consistencia para la escritura (writeConsistency)
          Seguidamente se pasa la función modify, esta función modify debe ser una función PURA y solo debe usar los valores
          pasados como parametros, no se debe acceder al sender desde esa funcion
         */
        replicator ! Update(DataKey, ORSet.empty[String], WriteLocal)(_ + s)
      } else {
        // remove
        log.info("Removing: {}", s)
        replicator ! Update(DataKey, ORSet.empty[String], WriteLocal)(_ - s)
      }
    /*
    * El case de abajo es el mensaje que recibe el actor como respuesta del replicator luego de la operación de Update
    * UpdateSuccess cuando la actualización fue exitosa y UpdateFailure cuando la actualización fue fallida. Cuando se recibe el
    * mensaje UpdateTimeOut no significa que la respuesta falló ni que tampoco o que hubo un roll back en la operación, significa que
    * el mensaje pudo haberse replicado a unos nodos y que será replicado a los demás a través del protocolo de chisme
    * */
    case _: UpdateResponse[_] =>
    //el mensaje Changed es la notifiación que el replicator envía al actor subscrito a los cambios de unos datos
    case c @ Changed(DataKey) =>
      val data: ORSet[String] = c.get(DataKey)
      log.info("Current elements: {}", data.elements)
  }

  override def postStop(): Unit = tickTask.cancel()

}