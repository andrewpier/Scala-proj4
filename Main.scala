import akka.actor.ActorSystem
import akka.actor.Props

object Main {
    def main(args: Array[String]): Unit = {
        val system = ActorSystem("argsfirst")
        val worker = system.actorOf(Props[Master], name = "lonely")
        worker ! StartCrawling(args)
    }
}