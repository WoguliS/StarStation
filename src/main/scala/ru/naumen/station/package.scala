import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.io.StdIn
import scala.util.{Failure, Success, Try}

/** Станция по обслуживанию космических кораблей */
object StarStation {

  /** Робот станции */
  object Robot {

    sealed trait Message

    case class ShipMessage(timeOfArrival: Int, handleTime: Int) extends Message
    case class Ship(timeOfArrival: Int, handleTime: Int, response: Int)
    case class GetShips(replyTo: ActorRef[Ships]) extends Message
    case class Ships(ships: Vector[Ship], isListEmpty: Boolean, currentTime: Int, index: Int)

    case class StationSize(numberOfPlaces: Int) extends Message
    case object DeleteLast extends Message

    case class Response(response: Int)

    def apply: Behaviors.Receive[Message] = apply(Vector.empty, -1, isListEmpty = true, currentTime = 0, index = 0)

    def apply(shipList: Vector[Ship], maxListSize: Int, isListEmpty: Boolean, currentTime: Int, index: Int): Behaviors.Receive[Message] = Behaviors.receive {

      case (ctx, ship @ ShipMessage(timeOfArrival, handleTime)) =>
        ctx.log.info(s"Ship message: $timeOfArrival, $handleTime")

        val previousShipOption: Option[Ship] = shipList.find(_.response != -1)

        val nextResponse = if (shipList.isEmpty && currentTime != 0)
          ship.timeOfArrival
        else if (shipList.isEmpty)
          ship.timeOfArrival + currentTime
        else if (!shipList.exists(_.response > -1))
          ship.timeOfArrival
        else if (hasPlace(shipList, maxListSize, timeOfArrival) &&
          ship.timeOfArrival > previousShipOption.get.response + previousShipOption.get.handleTime)
          ship.timeOfArrival
        else if (hasPlace(shipList, maxListSize, timeOfArrival))
          previousShipOption.get.response + previousShipOption.get.handleTime
        else -1

        val _currentTime = if (nextResponse == -1) currentTime
        else nextResponse

        apply(Ship(timeOfArrival, handleTime, nextResponse) +: shipList,
          maxListSize,
          isListEmpty = false,
          _currentTime,
          index)

      case (_, GetShips(replyTo))  =>
        replyTo ! Ships(shipList, isListEmpty, currentTime, index)
        Behaviors.same

      case (_, StationSize(maxSize)) =>
        apply(shipList, maxSize, isListEmpty, currentTime, index)

      case (_, DeleteLast) =>
        apply(shipList, maxListSize, shipList.size - index < 1, currentTime, index + 1)

      case _ =>
        apply(shipList, maxListSize, isListEmpty, currentTime, index)
    }

    /** Добавить описание для метода
     * @param shipList
     * @param maxListSize
     * @param timeOfArrival
     * @return
     */
    def hasPlace(shipList: Vector[Ship], maxListSize: Int, timeOfArrival: Int): Boolean = {
      @tailrec
      def loop(shipList: Vector[Ship], shipAcc: Int): Int = {
        if (shipList.isEmpty) shipAcc
        else if (shipList.head.response == -1) loop(shipList.tail, shipAcc)
        else if (timeOfArrival >= shipList.head.response + shipList.head.handleTime) shipAcc
        else loop(shipList.tail, shipAcc + 1)
      }

      maxListSize > loop(shipList, 0)
    }

  }

  /* Служебные имплисит поля от akka-http для сериализации десериализации тел запросов */
  implicit val shipFormat: RootJsonFormat[Robot.Ship] = jsonFormat3(Robot.Ship)
  implicit val shipsFormat: RootJsonFormat[Robot.Ships] = jsonFormat4(Robot.Ships)
  implicit val responseFormat: RootJsonFormat[Robot.Response] = jsonFormat1(Robot.Response)
  implicit val shipMessageFormat: RootJsonFormat[Robot.ShipMessage] = jsonFormat2(Robot.ShipMessage)
  implicit val stationSizeFormat: RootJsonFormat[Robot.StationSize] = jsonFormat1(Robot.StationSize)


  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[Robot.Message] = ActorSystem(Robot.apply, "robot")
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext: ExecutionContext = system.executionContext

    val auction: ActorRef[Robot.Message] = system
    import Robot._

    /** Маршрут POST/numberOfPlaces для получения размера станции */
    val numberOfPlacesRoute =
      path("numberOfPlaces") {
        post {
          entity(as[StationSize]) { stationSize =>
            auction ! stationSize
            complete(StatusCodes.OK, "")
          }
        }
      }

    /** Маршрут POST/ship для получения сообщения о корабле */
    val newShipRoute =
      path("ship") {
        post {
          entity(as[ShipMessage]) { shipMessage =>
            auction ! shipMessage
            complete(StatusCodes.OK, "")
          }
        }
      }

    /** Маршрут GET/next для передачи клиенту */
    val nextRoute =
      path("next") {
        get {
          implicit val timeout: Timeout = 5.seconds

          val response: Try[Future[Response]] =
            Try {
              val ships: Future[Ships] = (auction ? GetShips).mapTo[Ships]
              ships.map { ships =>
                val index = (ships.ships.size - ships.index) - 1
                val response = Response(ships.ships(index).response)
                auction ! DeleteLast
                response
              }
            }

          println(response)
          response match {
            case Success(response) => complete(StatusCodes.OK, response)
            case Failure(exception) => complete(StatusCodes.OK, "")
          }
        }
      }

    /** Маршрут GET/show_all для просмотра содержимого станции */
    val showShipsRoute =
      path("show_all") {
        get {
          implicit val timeout: Timeout = 5.seconds
          // query the actor for the current robot state
          val ships: Future[Ships] = (auction ? GetShips).mapTo[Ships]
          complete(StatusCodes.OK, ships)
        }
      }

    val allRoute = concat(numberOfPlacesRoute, newShipRoute, showShipsRoute, nextRoute)

    val bindingFuture = Http().newServerAt("localhost", 8888).bind(allRoute)
    println(s"Server online at http://localhost:8888/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done

  }

}