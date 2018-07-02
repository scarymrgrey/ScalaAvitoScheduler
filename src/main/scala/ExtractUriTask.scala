import java.io.FileNotFoundException
import java.util.Calendar

import com.mongodb.DBObject
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.{MongoClient, MongoClientURI}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.elementList
import scalaj.http.{Http, HttpResponse}

import scala.io.Source


abstract class Task extends Runnable {
  val host = "mongodb://104.41.225.182:27017/"
  val db = "cars"
  val dbClient: MongoDB = MongoClient(MongoClientURI(host))(db)
  val logs: MongoCollection = MongoClient(MongoClientURI(host))("logs")("exceptions")
  def getRestContent(url: String): HttpResponse[String] = {
    val request = Http(url)
    val headers = List(
      ("pragma", "no-cache"),
      ("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8"),
      ("accept-encoding", "gzip, deflate, br"),
      ("accept-language", "en-US,en;q=0.9,ru-RU;q=0.8,ru;q=0.7"),
      ("cache-control", "no-cache"),
      ("dnt", "1"),
      ("pragma", "no-cache"),
      ("upgrade-insecure-requests", "1"),
      ("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/67.0.3396.99 Safari/537.36"),
      ("x-compress", "null"),
      ("cookie","_ga=GA1.2.1987432004.1527337306; cto_lwid=516461db-5c10-4203-983d-7811d714c87a; _vwo_uuid_v2=DBA863D062E693AC0C68428CF3CA7C2CA|c58b56713a74196f383324687ec1e51c; _ym_uid=15273373074807638; _vwo_uuid=DBA863D062E693AC0C68428CF3CA7C2CA; __gads=ID=b4dae9d575d720c1:T=1527337338:S=ALNI_Mab-3Yq_mT4fuQYaMvGdoye_w3cOA; addruid=Q1I5r27rT3M37j6wV3Y80A3rh5; _vwo_ds=3%3Aa_0%2Ct_0%3A0%241529960967%3A80.87427861%3A%3A%3A18_0%2C12_0; _ym_d=1529960971; _gid=GA1.2.751259822.1530449489; f=5.32e32548b6f3e9784b5abdd419952845a68643d4d8df96e9a68643d4d8df96e9a68643d4d8df96e9a68643d4d8df96e94f9572e6986d0c624f9572e6986d0c624f9572e6986d0c62ba029cd346349f36c1e8912fd5a48d02c1e8912fd5a48d0246b8ae4e81acb9fa143114829cf33ca746b8ae4e81acb9fa46b8ae4e81acb9fa143114829cf33ca7fbcd99d4b9f4cbda2157fc552fc06411bc8794f0f6ce82fe3de19da9ed218fe23de19da9ed218fe23de19da9ed218fe23de19da9ed218fe23de19da9ed218fe23de19da9ed218fe23de19da9ed218fe23de19da9ed218fe23de19da9ed218fe23de19da9ed218fe23de19da9ed218fe23de19da9ed218fe23de19da9ed218fe2cd57c837045ffdb29568c32d8411f4f681997d94b754993e6e891cfcac525d71dd15986929784b9c37863d73204ac566043d98d6bd71c1373289d1e42347cd9c762e952a876cf4235fa97362c67ac7e28732de926882853a9d9b2ff8011cc827c4d07ec9665f0b70915ac1de0d03411290a12a53140f83c0f1995ee37b5cbe6e2da10fb74cac1eab2da10fb74cac1eab2c951d5df734fbe3806fa3435f270338517083b9c0063862; _ym_isad=1; bltsr=1; cmtchd=yes; crookie=Zl2PA4kKI1SX9D6EwmAYNT1sr2SxKTSYTyiXBpNk9No0B56MPgVwN9iI6X45nxafesqICkt4ZvpzKvSKUT2kOEx07MQ=; abp=1; _vis_opt_s=4%7C; _vis_opt_test_cookie=1; rheftjdd=rheftjddVal; view=list; buyer_selected_search_radius0=0; sx=H4sIAAAAAAACA4uOBQApu0wNAgAAAA%3D%3D; buyer_location_id=653240; _nfh=c8799290bbdd683ee31e4c9c8870ca6c; nps_sleep=1; _gat_UA-2546784-1=1; v=1530491676; u=2b0hbpcs.134qzif.fsv7v6yn56; sessid=9803cc41d2ccbc6d6d3c4d2a41747347.1530491676; dfp_group=37")
    )
    val req = request.headers(headers)
    req.asString
  }
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
                getRestContent("https://www.avito.ru/belarus?verifyUserLocation=1")
              } catch {
                case a: Throwable => println(a)
              }

              val d2 = getRestContent(s"https://www.avito.ru/moskva/avtomobili/s_probegom/${car.get("Make")}/${car.get("Model")}" +
                s"?view=list&radius=0&p=${car.get("Page")}")

              val doc = browser.parseString(d2.body)
              val items = doc >> elementList(".item.item_list.js-catalog-item-enum.item_car a.description-title-link")

              if (d2.code != 200 || d2.body.contains("Доступ временно заблокирован") || items.isEmpty)
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

