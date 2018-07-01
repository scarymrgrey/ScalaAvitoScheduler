import com.mongodb.DBObject
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.{MongoClient, MongoClientURI}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.elementList
import org.jsoup.HttpStatusException

import scala.collection.mutable


abstract class Task extends Runnable {
  val host = "mongodb://10.0.0.5:27017/"
  val db = "cars"
  val dbClient = MongoClient(MongoClientURI(host))(db)
}

case class ExtractUriTask() extends Task {
  override def run(): Unit = {
    val browser = JsoupBrowser()
    browser.clearCookies()
    var continueSeek = true
    var allItems = new mutable.ListBuffer[String]
    val cars = dbClient("cars")
    while (continueSeek) {
      cars.findOne(MongoDBObject("Complete" -> false))
        .fold({
          continueSeek = false
        }) {
          car: DBObject =>
            try {
              val doc = browser
                .get(s"https://www.avito.ru/moskva/avtomobili/s_probegom/${car.get("Make")}/${car.get("Model")}" +
                  s"?view=list&radius=0&p=${car.get("Page")}")
              val items = doc >> elementList(".item.item_list.js-catalog-item-enum.item_car a.description-title-link")

              if (items.isEmpty) {
                cars.update(MongoDBObject("_id" -> car.get("_id")),
                  $set("Complete" -> true),
                  upsert = false,
                  multi = true)
              } else {
                allItems ++= items.map(r => r.attr("href")).distinct
                val carMakeCollection = dbClient(car.get("Make").toString)
                println(s"Adding ${car.get("Model")}...")
                allItems.foreach(r =>
                  carMakeCollection.insert(MongoDBObject("Model" -> car.get("Model"), "URI" -> r)))

                cars.update(MongoDBObject("_id" -> car.get("_id")),
                  $set("Page" -> (car.get("Page").asInstanceOf[Int] + 1)),
                  upsert = false,
                  multi = true)
              }
            } catch {
              case ex: HttpStatusException if ex.getStatusCode == 404 => {
                cars.update(MongoDBObject("_id" -> car.get("_id")),
                  $set("Complete" -> true),
                  upsert = false,
                  multi = true)
                println(ex)
              }
              case ex => {
                continueSeek = false
                println(ex)
              }
            }
        }
    }
  }
}

