import java.io.FileNotFoundException
import java.util.Calendar

import com.mongodb.DBObject
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.{MongoClient, MongoClientURI}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.elementList

import scala.io.Source


abstract class Task extends Runnable {
  val host = "mongodb://mongodb:27017/"
  val db = "cars"
  val dbClient: MongoDB = MongoClient(MongoClientURI(host))(db)
  val logs: MongoCollection = MongoClient(MongoClientURI(host))("logs")("exceptions")
}

case class ExtractUriTask() extends Task {
  override def run(): Unit = {
    val browser = JsoupBrowser()
    browser.clearCookies()
    var continueSeek = true
    val cars = dbClient("cars")
    while (continueSeek) {
      cars.findOne(MongoDBObject("Complete" -> false))
        .fold({
          continueSeek = false
        }) {
          car: DBObject =>
            try {
              try {
                Source.fromURL("https://www.avito.ru/belarus?verifyUserLocation=1")
              } catch {
                case a: Throwable => println(a)
              }

              val d2 = Source.fromURL(s"https://www.avito.ru/moskva/avtomobili/s_probegom/${car.get("Make")}/${car.get("Model")}" +
                s"?view=list&radius=0&p=${car.get("Page")}").mkString
              val doc = browser.parseString(d2)
              val items = doc >> elementList(".item.item_list.js-catalog-item-enum.item_car a.description-title-link")

              if (d2.contains("Доступ временно заблокирован") || items.isEmpty)
                throw new Exception("Blocked")

              val carMakeCollection = dbClient(car.get("Make").toString)
              items.map(_.attr("href"))
                .distinct
                .foreach(r =>
                  carMakeCollection.insert(MongoDBObject("Model" -> car.get("Model"), "URI" -> r, "Loaded" -> false)))

              cars.update(MongoDBObject("_id" -> car.get("_id")),
                $set("Page" -> (car.get("Page").asInstanceOf[Int] + 1)),
                upsert = false,
                multi = true)

            } catch {
              case ex: FileNotFoundException => {
                cars.update(MongoDBObject("_id" -> car.get("_id")),
                  $set("Complete" -> true),
                  upsert = false,
                  multi = true)
                logs.insert(MongoDBObject("Message" -> ex.getMessage,
                  "Task" -> "ExtractInfoFromUriTask",
                  "Time" -> Calendar.getInstance().getTime,
                  "Line" -> ex.getStackTrace.head.getLineNumber.toString))
                println(ex)
              }
              case ex => {
                continueSeek = false
                logs.insert(MongoDBObject("Message" -> ex.getMessage,
                  "Task" -> "ExtractInfoFromUriTask",
                  "Time" -> Calendar.getInstance().getTime,
                  "Line" -> ex.getStackTrace.head.getLineNumber.toString))
                println(ex)
              }
            }
        }
    }
  }
}

