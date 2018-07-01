
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


object Main extends App {

  println("Starting Tasks...")
  val system = akka.actor.ActorSystem("system")
  system.scheduler.schedule(2 minutes,25 minutes,ExtractInfoFromUriTask())
  system.scheduler.schedule(0 milliseconds,30 minutes,ExtractUriTask())

  println("Ending Tasks...")
}
