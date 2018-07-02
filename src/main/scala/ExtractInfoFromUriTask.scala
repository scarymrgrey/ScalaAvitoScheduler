import java.net.SocketTimeoutException
import java.util.Calendar

import com.mongodb.casbah.commons.MongoDBObject
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.model.TextNode
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.elementList


case class ExtractInfoFromUriTask() extends Task {
  def getValBy(doc: JsoupBrowser.JsoupDocument, attr: Array[String]): String = {
    doc >> elementList(attr.head).map(_.headOption.fold(getValBy(doc, attr.tail))(_.innerHtml))
  }

  override def run(): Unit = {

    val browser = JsoupBrowser()
    browser.clearCookies()

    val cars = dbClient("cars")
    cars
      .map(r => r.get("Make").toString)
      .toList.distinct
      .foreach { carMake =>
        val currentCarCollection = dbClient(carMake)
        val allItems = currentCarCollection.find(MongoDBObject("Loaded" -> false))
        val toRemove = ": ".toSet
        allItems.foreach { z => {

          val url = z.get("URI").toString
          try {
            val response = getRestContent(s"https://www.avito.ru$url")
            response.Code match {

              case 301 | 302 if response.Body.contains("Доступ временно заблокирован")
                | response.Location.contains("blocked")
              => throw new Exception("Blocked")

              case 301 | 302 => {
                currentCarCollection.findAndRemove(MongoDBObject("_id" -> z.get("_id")))
                logs.insert(MongoDBObject("Message" -> "Removed",
                  "Task" -> "ExtractInfoFromUriTask",
                  "Time" -> Calendar.getInstance().getTime,
                  "URL" -> url))
              }
              case _ => {
                val car: browser.DocumentType = browser
                  .parseString(response.Body)
                val price = (car >> elementList("span.js-item-price")
                  .map(_.headOption.fold("-1")(_.innerHtml)))
                  .replace(" ", "").toInt

                if (price == -1)
                  throw new Exception("Blocked: Price = -1")

                val address = url.split("/")(1)

                val items = car >> elementList("li.item-params-list-item")
                val res = items.map(x => {
                  val span = x >> elementList(".item-params-label")
                  val key = span.head.innerHtml.filterNot(toRemove)
                  val strValue = x.childNodes.last.asInstanceOf[TextNode].content.filterNot(toRemove)
                  val value = key match {
                    case "Пробег" | "Годвыпуска" =>
                      strValue.replaceAll("[^0-9]", "").toInt
                    case "Объёмдвигателя" => strValue.filterNot("л+ ".toSet).replace(",",".").toDouble
                    case "Мощностьдвигателя" => strValue.filterNot("лс. c+".toSet).toInt
                    case _ => strValue
                  }
                  (key, value)
                }).filter({ case (key, value) => !(key contains "VIN") })
                  .toArray

                val objToUpdate = MongoDBObject.newBuilder
                objToUpdate += "Model" -> z.get("Model")
                objToUpdate += "URI" -> z.get("URI")
                objToUpdate += "Loaded" -> true
                objToUpdate += "Price" -> price
                objToUpdate += "Address" -> address
                res.foreach { obj =>
                  objToUpdate += obj._1 -> obj._2
                }

                currentCarCollection.update(MongoDBObject("_id" -> z.get("_id")),
                  objToUpdate.result(),
                  upsert = false,
                  multi = true)
              }
            }
          } catch {
            case ex : SocketTimeoutException => println(ex)
            case ex: Throwable => {
              logs.insert(MongoDBObject("Message" -> ex.getMessage,
                "Task" -> "ExtractInfoFromUriTask",
                "Time" -> Calendar.getInstance().getTime,
                "Line" -> ex.getStackTrace.head.getLineNumber.toString,
                "URL" -> url))
              println(ex)
              return
            }
          }
        }
        }
      }
  }
}
