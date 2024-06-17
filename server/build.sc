import mill._, scalalib._

object server extends RootModule with ScalaModule {
  def scalaVersion = "3.4.2"
  def ivyDeps = Agg(
    ivy"com.typesafe:config:1.4.3",
    ivy"co.fs2::fs2-core:3.9.3",
    ivy"co.fs2::fs2-io:3.9.3",
    ivy"org.gnieh::fs2-data-csv:1.10.0",
    ivy"org.gnieh::fs2-data-json-circe:1.10.0",
    ivy"com.typesafe.scala-logging::scala-logging:3.9.4",
    ivy"ch.qos.logback:logback-classic:1.2.3",
    ivy"org.typelevel::log4cats-slf4j:2.6.0".withDottyCompat(scalaVersion()),
    ivy"io.circe::circe-core:0.14.1",
    ivy"io.circe::circe-generic:0.14.1",
    ivy"io.circe::circe-parser:0.14.1",
    ivy"com.github.scopt::scopt:4.1.0",
    ivy"com.github.tototoshi::scala-csv:1.3.10",
    ivy"org.http4s::http4s-ember-client:1.0.0-M41",
    ivy"org.http4s::http4s-ember-server:1.0.0-M41",
    ivy"org.http4s::http4s-dsl:1.0.0-M41"
  )

  object test extends ScalaTests {
    def ivyDeps = Agg(ivy"com.lihaoyi::utest:0.7.11")
    def testFramework = "utest.runner.Framework"
  }

}
