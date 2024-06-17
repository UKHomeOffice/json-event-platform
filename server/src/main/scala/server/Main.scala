package server

import cats.effect.{ExitCode, IO, IOApp}
import cats.effect.std.Queue
import cats.effect.kernel.Async
import cats.implicits._
import cats.syntax.all.*

import fs2.{Pipe, Stream}
import fs2.concurrent.Topic
import fs2.io.net.Network
import fs2.io.file.Files

import com.comcast.ip4s._

import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.ember.server._
import org.http4s.headers._
import org.http4s.server.middleware.Logger
import org.http4s.server.staticcontent._

import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame

import scala.concurrent._
import scala.concurrent.duration._

import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.{AuthedRoutes, Header}
import org.http4s.implicits._

import org.typelevel.log4cats._
import org.typelevel.log4cats.slf4j.Slf4jFactory

import com.typesafe.config.{Config, ConfigFactory}
import org.typelevel.ci._

import io.circe._
import io.circe.parser._

object Program extends IOApp.Simple with Http4sDsl[IO] {
  val config = ConfigFactory.load()
  val authToken = config.getString("authToken")

  implicit val logging: LoggerFactory[IO] = Slf4jFactory.create[IO]
  val logger = logging.getLogger(LoggerName("server"))

  def getBodyAsString(request :Request[IO]) :IO[String] =
    request.body.through(fs2.text.utf8Decode).compile.string

  def service (
      wsb: WebSocketBuilder2[IO],
      q: Queue[IO, WebSocketFrame],
      t: Topic[IO, WebSocketFrame]
  ): HttpApp[IO] = {
    HttpRoutes.of[IO] {

      case request @ POST -> Root / "broadcast" =>

        val headers = request.headers

        getBodyAsString(request).flatMap { body =>

          headers.get(ci"x-api-token") match {
            case Some(suppliedToken) if suppliedToken.head.value.trim == authToken =>
              logger.info(s"Broadcasting: $body").flatMap { _ =>
                q.offer(WebSocketFrame.Text(body)).flatMap { _ =>
                  Ok("broadcast sent")
                }
              }
            case _ =>
              logger.info(s"rejecting broadcast message: $body").flatMap { _ =>
                Forbidden("Incorrect Authorisation Token")
              }
          }
        }

      case GET -> Root / "ws" =>
        val send: Stream[IO, WebSocketFrame] = {
          t.subscribe(maxQueued = 1000)
        }

        val receive: Pipe[IO, WebSocketFrame, Unit] = {

          _.foreach {
            case WebSocketFrame.Text(text, _) =>

              extractBroadcastMessage(text) match {
                case Some(message) => q.offer(WebSocketFrame.Text(message))
                case None => logger.info(s"CLIENT LOG: $text")
              }

          }
        }

        wsb.build(send, receive)
    }
  }.orNotFound

  def program: IO[Unit] = {
    for {
      q <- Queue.unbounded[IO, WebSocketFrame]
      t <- Topic[IO, WebSocketFrame]
      s <- Stream(
        Stream.fromQueueUnterminated(q).through(t.publish),
        Stream
          .awakeEvery[IO](30.seconds)
          .map(_ => WebSocketFrame.Ping())
          .through(t.publish),
        Stream.eval(server(q, t))
      ).parJoinUnbounded.compile.drain
    } yield s
  }

  def server(
    q: Queue[IO, WebSocketFrame],
    t: Topic[IO, WebSocketFrame]
  ): IO[Unit] = {
    val host = host"0.0.0.0"
    val port = port"8080"
    EmberServerBuilder
      .default[IO]
      .withHost(host)
      .withPort(port)
      .withHttpWebSocketApp(wsb => service(wsb, q, t))
      .build
      .useForever
      .void
  }

  def extractBroadcastMessage(text :String) :Option[String] = {

    parse(text) match {
      case Left(_) => None
      case Right(json) =>

        for {
          messageType <- json.hcursor.downField("messageType").as[String].toOption if messageType == "broadcast"
          authToken <- json.hcursor.downField("authToken").as[String].toOption if authToken == authToken
          message <- json.hcursor.downField("message").as[String].toOption
        } yield { message }
    }
  }

  override def run: IO[Unit] = program

}

