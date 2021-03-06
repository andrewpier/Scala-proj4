import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorRef

// Diagram of Actors and messages
//
//                                       |
//                                       |
//                                 StartCrawling
//                                       |
//                                       V

/////////////  <-- IndexRequest --   ////////////  <----- Query ----- //////////////
// Fetcher //                        // Master //                     // Prompter //
///////////// -- Option[RawPage] --> ////////////  -- QueryResult --> //////////////


//////////////////////////////////
// Case classes for messages

case class StartCrawling(urls: Seq[String])

case class IndexRequest(url: String)
case class RawPage(url: String, html: String) {
  // Override toString so printed messages do not
  //   dump the full html
  override def toString() = url
}

case class Query(terms: Seq[String])
case class QueryResult(fractionContaining: Double, numTotalPages: Int)

//////////////////////////////////
// Actors

// TODO: write the Fetcher class  
class Fetcher extends Actor {
    
    def receive = {
        case IndexRequest(url) => {
            try{
                var count = 0
                val src = scala.io.Source.fromURL( url ).getLines.mkString("\n")
                sender ! Some(RawPage(url,src))
            }catch{
                case e:  java.io.IOException => sender ! None
            }
        }
    }
    
    
}


// Prompter asks the user to enter queries,
//   then sends them along to the Master,
//   receiving and displaying results
class Prompter extends Actor {
 
  // To add logging of messages received,
  //   1. edit the application.conf file
  //   2. use the following line instead of def receive = {
  //def receive = akka.event.LoggingReceive {
  
  def receive = {
    case QueryResult(fracContaining, numTotalPages) => {
      if(numTotalPages > 0){
        println((fracContaining*100.0) + "% of " + numTotalPages + " total pages matched.")
      }
      
      //Prompt for the next query
      val q = scala.io.StdIn.readLine("Enter a query: ")
      
      sender ! Query(if(q.length == 0) Nil else q.split("(_|\\W)+").map(_.toLowerCase))
    }
  }
}

class Master extends Actor {
  // TODO: Set maxPages lower for initial testing
  val maxPages = 50

  val urlsToIndex = scala.collection.mutable.HashSet[String]()
  
  // indexedUrls duplicates information with indexedPages,
  //   but indexedUrls is quick to check for duplicates
  val indexedPages = scala.collection.mutable.ListBuffer[Page]()
  val indexedUrls = scala.collection.mutable.HashSet[String]()
  
  def receive = {
   
    // TODO: handle StartIndexing message
    case StartCrawling(urls) => {
        val workers = for (u <- 0 until urls.size) yield context.actorOf(Props[Fetcher],name = (s"fetcher-${u}"))
        urls.zipWithIndex.foreach(thing => {workers(thing._2 ) ! IndexRequest(thing._1)})
        val prompter = context.actorOf(Props[Prompter],name = "prompter")
        prompter ! QueryResult(0,0)
    }
      
    // TODO: handle Query message
   
    case Query(terms) => {
        if(terms.size == 0) context.system.shutdown()
        else{
            var count = 0
            for( page <- indexedPages) if(page.containsAll(terms)) count = count + 1
            val percentage = count.toDouble / indexedPages.size.toDouble
            sender ! QueryResult(percentage, indexedUrls.size)
        }
    }
    case x: Option[_] => {
      // See if we got a RawPage back
        
      x match {
        case Some(RawPage(url, html)) => {
          // Add the page to the indexed collections
          val pg = new Page(url, html)
          indexedPages += pg
          indexedUrls += url
      
          // Add the links found in the page to the set of links to index

          urlsToIndex ++= pg.getLinks.filter( link => !urlsToIndex.contains(link) && !indexedUrls.contains(link) )

        }
        case _ => Unit
      }  
      
      // Regardless of whether or not we got a RawPage,
      //   we should send another request to the Fetcher
      if (!urlsToIndex.isEmpty && indexedUrls.size < maxPages) {
        val nextUrl = urlsToIndex.head
        urlsToIndex.remove(nextUrl)
        sender ! IndexRequest(nextUrl)
      }
      // If urlsToIndex is empty, we should do something to prevent
      //   the sender Fetcher from remaining idle,
      //   but that is beyond our scope
    }
        
  }

}




    