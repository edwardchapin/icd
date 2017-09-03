import sbt._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

object Dependencies {
  val Version = "0.10"
  val ScalaVersion = "2.12.3"

  // command line dependencies
  val scopt = "com.github.scopt" %% "scopt" % "3.7.0"
  val jsonSchemaValidator = "com.github.fge" % "json-schema-validator" % "2.2.6"
  val ficus = "com.iheart" %% "ficus" % "1.4.1"
  val typesafeConfig = "com.typesafe" % "config" % "1.3.1"
  val scalaTest = "org.scalatest" %% "scalatest" % "3.0.1"

  // XXX: FIXME: Deprecated! replace with https://github.com/vsch/flexmark-java
  val pegdown = "org.pegdown" % "pegdown" % "1.6.0"

  val itextpdf = "com.itextpdf" % "itextpdf" % "5.5.12"
  val xmlworker = "com.itextpdf.tool" % "xmlworker" % "5.5.12"
  val casbah = "org.mongodb" %% "casbah" % "3.1.1"

  // XXX: FIXME: tried newer version, but changes in API means more work needed on this side...
//  val diffson = "org.gnieh" %% "diffson" % "1.1.0"
  val diffson = "org.gnieh" %% "diffson" % "2.2.1"

  val sprayJson = "io.spray" %%  "spray-json" % "1.3.3"
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2"
  val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"
  val scalatags = "com.lihaoyi" %% "scalatags" % "0.6.5"
  val jsoup = "org.jsoup" % "jsoup" % "1.10.3"
  val jgit = "org.eclipse.jgit" % "org.eclipse.jgit" % "4.8.0.201706111038-r"

  // web server dependencies
  val scalajsScripts = "com.vmunier" %% "scalajs-scripts" % "1.1.1"

  // XXX: FIXME: Deprecated: Use play-json macros instead
  val upickle = "com.lihaoyi" %% "upickle" % "0.4.1"

  val jqueryUi = "org.webjars" % "jquery-ui" % "1.12.1"
  val webjarsPlay = "org.webjars" %% "webjars-play" % "2.6.2"
  val bootstrap = "org.webjars" % "bootstrap" % "3.3.7-1"
  val bootstrapTable = "org.webjars.bower" % "bootstrap-table" % "1.11.1"

  // ScalaJS web client scala dependencies
  val clientDeps = Def.setting(Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.9.2",
    "com.lihaoyi" %%% "scalatags" % "0.6.5",

    // XXX: FIXME: Deprecated: Use play-json (Works in ScalaJS?)
    "com.lihaoyi" %%% "upickle" % "0.4.1",
    "org.querki" %%% "jquery-facade" % "1.0",
    "com.github.japgolly.scalacss" %%% "core" % "0.5.3",
    "com.github.japgolly.scalacss" %%% "ext-scalatags" % "0.5.3"
  ))

  // ScalaJS client JavaScript dependencies
  val clientJsDeps = Def.setting(Seq(
    "org.webjars" % "jquery" % "3.2.1" / "jquery.js" minified "jquery.min.js",
    "org.webjars" % "jquery-ui" % "1.12.1" / "jquery-ui.min.js" dependsOn "jquery.js",
    "org.webjars" % "bootstrap" % "3.3.7-1" / "bootstrap.min.js" dependsOn "jquery.js",
    "org.webjars.bower" % "bootstrap-table" % "1.11.1" / "bootstrap-table.min.js",
    ProvidedJS / "resize.js" dependsOn "jquery-ui.min.js"
  ))
}

