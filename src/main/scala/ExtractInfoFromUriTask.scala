import java.util.Calendar

import com.mongodb.casbah.commons.MongoDBObject
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.model.TextNode
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.elementList

import scala.io.Source

case class ExtractInfoFromUriTask() extends Task {
  override def run(): Unit = {
    val browser = JsoupBrowser()
    browser.clearCookies()
    val cars = dbClient("cars")
    cars.foreach { r =>
      val currentCarCollection = dbClient(r.get("Make").toString)
      val allItems = currentCarCollection.find(MongoDBObject("Loaded" -> false))
      val toRemove = ": ".toSet
      allItems.foreach(z => {
        try {
          val url = z.get("URI").toString
          val car2 = Source.fromURL(s"https://avito.ru$url").mkString
          val car = browser.parseString(car2)
          val price = (car >> elementList("span.js-item-price").map(_.headOption.fold("0")(_.innerHtml)))
            .replace(" ", "").toInt
          val items = car >> elementList("li.item-params-list-item")
          val res = items.map(x => {
            val span = x >> elementList(".item-params-label")
            val key = span.head.innerHtml
            val value = x.childNodes.last.asInstanceOf[TextNode].content
            (key.filterNot(toRemove), value.filterNot(toRemove))
          }).filter({ case (key, value) => !(key contains "VIN") })
            .toArray
          val objToUpdate = MongoDBObject.newBuilder
          objToUpdate += "Model" -> z.get("Model")
          objToUpdate += "URI" -> z.get("URI")
          objToUpdate += "Loaded" -> true
          objToUpdate += "Price" -> price
          res.foreach { obj =>
            objToUpdate += obj._1 -> obj._2
          }

          currentCarCollection.update(MongoDBObject("_id" -> z.get("_id")),
            objToUpdate.result(),
            upsert = false,
            multi = true)
        } catch {
          case ex: java.net.ConnectException => {
            logs.insert(MongoDBObject("Message" -> ex.getMessage,
              "Task" -> "ExtractInfoFromUriTask",
              "Time" -> Calendar.getInstance().getTime,
              "Line" -> ex.getStackTrace.head.getLineNumber.toString))
            println(ex)
            return
          }
          case ex: Throwable => {
            logs.insert(MongoDBObject("Message" -> ex.getMessage,
              "Task" -> "ExtractInfoFromUriTask",
              "Time" -> Calendar.getInstance().getTime,
              "Line" -> ex.getStackTrace.head.getLineNumber.toString))
            println(ex)
          }
        }
      })
    }
  }
}
