package com.lunatech.service.user

import java.util.UUID

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import akka.http.scaladsl.server.Route
import com.lunatech.errors._
import com.lunatech.service.Routes
import io.circe.generic.auto._
import io.circe.syntax._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

class UserRoutes(val userService: UserService) extends Routes {

  val userRoutes: Route = internal.routes

  private object internal {

    implicit val httpErrorMapper: ErrorMapper[ServiceError, HttpError] =
      Routes.buildErrorMapper(ServiceError.httpErrorMapper)

    implicit class ErrorOps[E <: ServiceError, A](result: Future[Either[E, A]]) {
      def toRestError[G <: HttpError](implicit errorMapper: ErrorMapper[E, G]): Future[Either[G, A]] = result.map {
        case Left(error) => Left(errorMapper(error))
        case Right(value) => Right(value)
      }
    }
    
    def completeEither[E <: ServiceError, R: ToEntityMarshaller]
    (statusCode: StatusCode, either: => Either[E, R])(
      implicit mapper: ErrorMapper[E, HttpError]
    ): Route = {
      either match {
        case Left(value) => complete(value.statusCode, ErrorResponse(code = value.code, message = value.message))
        case Right(value) => complete(value)
      }
    }

    def routes: Route = {
      pathPrefix(version)(
        userManagement
      )
    }

    def userManagement: Route =
      pathPrefix("users") {
        userActions ~ getUsers ~ postUser
      }

    def getUsers: Route =
      get(
        onComplete(userService.getUsers) {
          case Success(future) =>
            completeEither(StatusCodes.OK, future)
          case Failure(ex) => complete((StatusCodes.InternalServerError, s"An error occurred: ${ex.getMessage}"))
        }
      )

    def postUser: Route =
      post {
        entity(as[UserCreate]) { userCreate =>
          onComplete(userService.createUser(userCreate)) {
            case Success(future) =>
              completeEither(StatusCodes.Created, future)
            case Failure(ex) => complete((StatusCodes.InternalServerError, s"An error occurred: ${ex.getMessage}"))
          }
        }
      }

    def userActions: Route =
      pathPrefix(Segment) { id =>
        val userId = UUID.fromString(id)
        getUser(userId) ~ putUser(userId) ~ deleteUser(userId) ~ patchUser(userId)
      }

    def getUser(userId: UUID): Route =
      get(
        onComplete(userService.getUser(userId)) {
          case Success(future) =>
            completeEither(StatusCodes.OK, future)
          case Failure(ex) => complete((StatusCodes.InternalServerError, s"An error occurred: ${ex.getMessage}"))
        }
      )

    def putUser(userId: UUID): Route =
      put {
        entity(as[UpdateUser]) { updateUser =>
          onComplete(userService.updateUser(userId, updateUser)) {
            case Success(future) =>
              completeEither(StatusCodes.OK, future)
            case Failure(ex) => complete((StatusCodes.InternalServerError, s"An error occurred: ${ex.getMessage}"))
          }
        }
      }

    def patchUser(userId: UUID): Route =
      patch {
        entity(as[UpdateUser]) { updateUser =>
          onComplete(userService.updateUser(userId, updateUser)) {
            case Success(future) =>
              completeEither(StatusCodes.OK, future)
            case Failure(ex) => complete((StatusCodes.InternalServerError, s"An error occurred: ${ex.getMessage}"))
          }
        }
      }

    def deleteUser(userId: UUID): Route =
      delete {
        onComplete(userService.deleteUser(userId)) {
          case Success(future) =>
            completeEither(StatusCodes.NoContent, future)
          case Failure(ex) => complete((StatusCodes.InternalServerError, s"An error occurred: ${ex.getMessage}"))
        }
      }
  }

}
