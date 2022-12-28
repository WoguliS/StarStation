package ru.naumen.station

case class Station(numberOfPlaces:Int)
package ru.naumen.station

import akka.http.scaladsl.server.Route
import akka.Done
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import spray.json.DefaultJsonProtocol.{jsonFormat1, jsonFormat2}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.io.StdIn



object StarStation extends App {

  lazy val station:Station = createStation(_=>)
  def createStation(size:Int)=

  implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "my-system")
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext: ExecutionContextExecutor = system.executionContext

  var orders: List[Item] = Nil

  // domain model
  final case class Item(name: String, id: Long)

  final case class Order(items: List[Item])
  final case class Parameters(a:Double, b:Double, c:Double)

  final case class StationSize(numberOfPlaces:Int)
  final case class Ship(timeOfArrival:Int, handleTime:Int)

  final case class Response(response:Int)

  // formats for unmarshalling and marshalling
  implicit val itemFormat: RootJsonFormat[Item] = jsonFormat2(Item)
  implicit val orderFormat: RootJsonFormat[Order] = jsonFormat1(Order)
  implicit val parametersFormat: RootJsonFormat[Parameters] = jsonFormat3(Parameters)

  implicit val stationSizeFormat: RootJsonFormat[StationSize] = jsonFormat1(StationSize)
  implicit val shipFormat: RootJsonFormat[Ship] = jsonFormat2(Ship)
  implicit val responseFormat: RootJsonFormat[Response] = jsonFormat1(Response)

  // (fake) async database query api
  def fetchItem(itemId: Long): Future[Option[Item]] = Future {
    orders.find(o => o.id == itemId)
  }

  def saveOrder(order: Order): Future[Done] = {
    orders = order match {
      case Order(items) => items ::: orders
      case _ => orders
    }
    Future {
      Done
    }
  }

  val numberOfPlacesRoute: Route =
    post {
      path("numberOfPlaces") {
        entity(as[StationSize]) { stationSize =>
          complete("")

        }
      }
    }

  val shipRoute: Route =
    post {
      path("ship") {
        entity(as[Ship]) { ship =>
          complete("")

        }
      }
    }


  val nextRoute: Route =

    get{
      path("next") {
        val response = Response(222)
        complete(response)
      }
    }


  val routeExample: Route =
    concat(
      get {
        pathPrefix("item" / LongNumber) { id =>
          // there might be no item for a given id
          val maybeItem: Future[Option[Item]] = fetchItem(id)

          onSuccess(maybeItem) {
            case Some(item) => complete(item)
            case None => complete(StatusCodes.NotFound)
          }
        }
      },
      post {
        path("create-order") {
          entity(as[Order]) { order =>
            val saved: Future[Done] = saveOrder(order)
            onSuccess(saved) { _ => // we are not interested in the result value `Done` but only in the fact that it was successful
              complete("order created")
            }
          }
        }
      }
    )

  val route = {
    get {
      path("hello"){
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
      }
    }
  }

  val calculateDiscriminant = {
    path("calculate_discriminant"){
      post{
        entity(as[Parameters]) { parameters =>

          val discriminant = new Discriminant().calculate(parameters.a, parameters.b, parameters.c)
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"<h1>$discriminant\n,${parameters.a}, ${parameters.b}, ${parameters.c} </h1>"))
        }
      }
    }
  }

  val allRoutes = concat(route,routeExample,calculateDiscriminant,nextRoute,numberOfPlacesRoute,shipRoute)

  val bindingFuture = Http().newServerAt("localhost", 8888).bind(allRoutes)

  println(s"Server now online. Please navigate to http://localhost:8888/hello\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done

  println("Hello world!")
}
