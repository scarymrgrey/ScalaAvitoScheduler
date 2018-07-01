
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


object Main extends App {

  println("Starting ExtractUriTask...")
  val system = akka.actor.ActorSystem("system")
  system.scheduler.schedule(
    0 milliseconds,
    30 minutes,
    ExtractUriTask())

}
