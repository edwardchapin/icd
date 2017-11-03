package controllers

import java.io.ByteArrayOutputStream
import javax.inject._

import csw.services.icd.IcdToPdf
import csw.services.icd.db.ApiVersions.ApiEntry
import csw.services.icd.db.IcdVersionManager.{SubsystemAndVersion, VersionDiff}
import csw.services.icd.db._
import csw.services.icd.github.IcdGitManager
import icd.web.shared.IcdModels.SubsystemModel
import icd.web.shared.{IcdVersion, _}
import org.webjars.play._
import play.api.libs.json.Json
import play.filters.csrf.{CSRF, CSRFAddToken, CSRFCheck}
import play.api.mvc._
import play.api.{Environment, Mode}

// Defines the database used
object Application {
  // Used to access the ICD database
  val db = IcdDb()
}

/**
  * Provides the interface between the web client and the server
  */
@Singleton
class Application @Inject()(env: Environment, addToken: CSRFAddToken, checkToken: CSRFCheck, assets: AssetsFinder, webJarsUtil: WebJarsUtil, webJarAssets: WebJarAssets, components: ControllerComponents) extends AbstractController(components) {

  import Application._
  import JsonSupport._

  // cache of API and ICD versions published on GitHub (until next browser refresh)
  val (allApiVersions, allIcdVersions) = IcdGitManager.getAllVersions

  // Somehow disabling the CSRF filter in application.conf and adding it here was needed to make this work
  // (The CSRF token is needed for the file upload dialog in the client)
  def index = addToken(Action { implicit request =>
    implicit val environment: Environment = env
    val token = Csrf(CSRF.getToken.get.value)
    val debug = env.mode == Mode.Dev
    Ok(views.html.index(debug, assets, token, webJarsUtil))
  })

  /**
    * Gets a list of top level subsystem names
    */
  def subsystemNames = Action { implicit request =>
    val subsystemsInDb = db.query.getSubsystemNames
    val publishedSubsystemNames = allApiVersions.map(_.subsystem)
    val names = publishedSubsystemNames ++ subsystemsInDb
    Ok(Json.toJson(names.sorted.toSet))
  }

  /**
    * Ingests a published subsystem and returns the db model, if found
    *
    * @param subsystem  the subsystem name
    * @param versionOpt optional version to ingest (otherwise all versions)
    * @return the subsystem database model
    */
  private def ingestPublishedSubsystem(subsystem: String, versionOpt: Option[String]): Option[SubsystemModel] = {

    // Gets the matching published ApiEntry for the subsystem version
    def getApiEntry(apiVersions: ApiVersions): Option[ApiEntry] = versionOpt match {
      case Some(version) => apiVersions.apis.find(_.version == version)
      case None => Some(apiVersions.apis.head)
    }

    allApiVersions.find(a => a.subsystem == subsystem).flatMap(getApiEntry) match {
      case Some(apiEntry) =>
        val sv = SubsystemAndVersion(subsystem, Some(apiEntry.version))
        IcdGitManager.ingest(db, sv, List(apiEntry), println(_))
        db.versionManager.getSubsystemModel(subsystem, versionOpt)
      case None => None
    }
  }

  /**
    * Gets information about a named subsystem
    */
  def subsystemInfo(subsystem: String, versionOpt: Option[String]) = Action { implicit request =>

    // Gets the matching subsystem info from GitHub, if published there
    def getPublishedSubsystemInfo: Option[SubsystemInfo] = {
      ingestPublishedSubsystem(subsystem, versionOpt).map { model =>
        SubsystemInfo(model.subsystem, versionOpt, model.title, model.description)
      }
    }

    // Get the subsystem info from the database, or if not found, look in the published GitHub repo
    db.versionManager.getSubsystemModel(subsystem, versionOpt) match {
      case Some(model) =>
        // Found in db
        val info = SubsystemInfo(model.subsystem, versionOpt, model.title, model.description)
        Ok(Json.toJson(info))
      case None =>
        // Not found in db, check if its a published version on GitHub, and if so, ingest it first
        getPublishedSubsystemInfo match {
          case Some(info) =>
            Ok(Json.toJson(info))
          case None =>
            NotFound
        }
    }
  }

  /**
    * Gets a list of components belonging to the given version of the given subsystem
    */
  def components(subsystem: String, versionOpt: Option[String]) = Action { implicit request =>
    if (db.versionManager.getSubsystemModel(subsystem, versionOpt).isEmpty) {
      // Not found in db, check if its a published version on GitHub, and if so, ingest it first
      ingestPublishedSubsystem(subsystem, versionOpt) // Make sure subsystem is ingested from GitHub if needed
    }

    val names = db.versionManager.getComponentNames(subsystem, versionOpt)
    Ok(Json.toJson(names))
  }

  /**
    * Gets information about a named component in the given version of the given subsystem
    *
    * @param subsystem  the subsystem
    * @param versionOpt the subsystem's version (default: current)
    * @param compNames  component names to get info about (separated by ",")
    */
  def componentInfo(subsystem: String, versionOpt: Option[String], compNames: String) = Action { implicit request =>
    val compNameList = compNames.split(",").toList
    val infoList = ComponentInfoHelper.getComponentInfoList(db, subsystem, versionOpt, compNameList)
    Ok(Json.toJson(infoList))
  }

  /**
    * Adds information about the ICD to the database if needed by ingesting it from the GitHub ICD repo
    */
  private def ingestPublishedIcd(icdVersion: IcdVersion): Unit = {
    val v = icdVersion.icdVersion
    if (v.nonEmpty && v != "*") {
      val sv = SubsystemAndVersion(icdVersion.subsystem, Some(icdVersion.subsystemVersion))
      val tv = SubsystemAndVersion(icdVersion.target, Some(icdVersion.targetVersion))
      val icds = db.versionManager.getIcdVersions(icdVersion.subsystem, icdVersion.target)
      if (!icds.toSet.contains(v))
        IcdGitManager.importIcdFiles(db, List(sv, tv), println(_))
    }
  }

  /**
    * Gets information about a component in a given version of a subsystem
    *
    * @param subsystem        the source subsystem
    * @param versionOpt       the source subsystem's version (default: current)
    * @param compNames        component names to get info about (separated by ",")
    * @param target           the target subsystem
    * @param targetVersionOpt the target subsystem's version
    */
  def icdComponentInfo(subsystem: String, versionOpt: Option[String], compNames: String,
                       target: String, targetVersionOpt: Option[String]) = Action { implicit request =>
    val compNameList = compNames.split(",").toList
    if (db.versionManager.getSubsystemModel(target, targetVersionOpt).isEmpty) {
      ingestPublishedSubsystem(target, targetVersionOpt)
    }
    val infoList = IcdComponentInfo.getComponentInfoList(db, subsystem, versionOpt, compNameList, target, targetVersionOpt)
    Ok(Json.toJson(infoList))
  }

  /**
    * Returns the PDF for the given ICD
    *
    * @param subsystem        the source subsystem
    * @param versionOpt       the source subsystem's version (default: current)
    * @param compNamesOpt     an optional comma separated list of component names to include (default: all)
    * @param target           the target subsystem
    * @param targetVersionOpt optional target subsystem's version (default: current)
    * @param icdVersionOpt    optional ICD version (default: current)
    */
  def icdAsPdf(subsystem: String, versionOpt: Option[String], compNamesOpt: Option[String],
               target: String, targetVersionOpt: Option[String],
               icdVersionOpt: Option[String]) = Action { implicit request =>

    val out = new ByteArrayOutputStream()
    val compNames = compNamesOpt match {
      case Some(s) => s.split(",").toList
      case None => db.versionManager.getComponentNames(subsystem, versionOpt)
    }

    // If the ICD version is specified, we can determine the subsystem and target versions, otherwise
    // if only the subsystem or target versions were given, use those (default to latest versions)
    val v = icdVersionOpt.getOrElse("*")

    // Make sure the database has the ICDs that were published on GitHub
    if (v != "*" && versionOpt.isDefined && targetVersionOpt.isDefined)
      ingestPublishedIcd(IcdVersion(v, subsystem, versionOpt.get, target, targetVersionOpt.get))

    val versions = db.versionManager.getIcdVersions(subsystem, target)
    val iv = versions.find(_.icdVersion.icdVersion == v).map(_.icdVersion)
    val (sv, tv) = if (iv.isDefined) {
      val i = iv.get
      (SubsystemWithVersion(Some(i.subsystem), Some(i.subsystemVersion)), SubsystemWithVersion(Some(i.target), Some(i.targetVersion)))
    } else {
      (SubsystemWithVersion(Some(subsystem), versionOpt), SubsystemWithVersion(Some(target), targetVersionOpt))
    }

    IcdDbPrinter(db).getIcdAsHtml(compNames, sv, tv, iv) match {
      case Some(html) =>
        IcdToPdf.saveAsPdf(out, html)
        val bytes = out.toByteArray
        Ok(bytes).as("application/pdf")
      case None =>
        NotFound
    }
  }

  /**
    * Returns the PDF for the given subsystem API
    *
    * @param subsystem    the source subsystem
    * @param versionOpt   the source subsystem's version (default: current)
    * @param compNamesOpt an optional comma separated list of component names to include (default: all)
    */
  def apiAsPdf(subsystem: String, versionOpt: Option[String], compNamesOpt: Option[String]) = Action { implicit request =>
    val out = new ByteArrayOutputStream()
    val compNames = compNamesOpt match {
      case Some(s) => s.split(",").toList
      case None => db.versionManager.getComponentNames(subsystem, versionOpt)
    }
    val sv = SubsystemWithVersion(Some(subsystem), versionOpt)
    IcdDbPrinter(db).getApiAsHtml(compNames, sv) match {
      case Some(html) =>
        IcdToPdf.saveAsPdf(out, html)
        val bytes = out.toByteArray
        Ok(bytes).as("application/pdf")
      case None =>
        NotFound
    }
  }

  /**
    * Returns a detailed list of the versions of the given subsystem
    */
  def getVersions(subsystem: String) = Action { implicit request =>
    val versions = allApiVersions.filter(_.subsystem == subsystem).flatMap(_.apis).map(a => VersionInfo(Some(a.version), a.user, a.comment, a.date))
    Ok(Json.toJson(versions))
  }

  /**
    * Returns a list of version names for the given subsystem
    */
  def getVersionNames(subsystem: String) = Action { implicit request =>
    val versions = allApiVersions.filter(_.subsystem == subsystem).flatMap(_.apis).map(_.version)
    Ok(Json.toJson(versions))
  }

  /**
    * Gets a list of ICD names as pairs of (subsystem, targetSubsystem)
    */
  def getIcdNames = Action { implicit request =>
    val list = allIcdVersions.map(i => IcdName(i.subsystems.head, i.subsystems.tail.head))
    val sorted = list.sortWith((a, b) => a.subsystem.compareTo(b.subsystem) < 0)
    Ok(Json.toJson(sorted))
  }

  /**
    * Gets a list of versions for the ICD from subsystem to target subsystem
    */
  def getIcdVersions(subsystem: String, target: String) = Action { implicit request =>
    // convert list to use shared IcdVersion class
    val list = allIcdVersions.find(i => i.subsystems.contains(subsystem) && i.subsystems.contains(target)).toList.flatMap(_.icds).map { icd =>
      val icdVersion = IcdVersion(icd.icdVersion, subsystem, icd.versions.head, target, icd.versions.tail.head)
      IcdVersionInfo(icdVersion, icd.user, icd.comment, icd.date)
    }
    Ok(Json.toJson(list))
  }

  // Packages the diff information for return to browser
  private def getDiffInfo(diff: VersionDiff): DiffInfo = {
    DiffInfo(diff.path, diff.patch.toString())
  }

  /**
    * Gets the difference between two subsystem versions
    */
  def diff(subsystem: String, versionsStr: String) = Action { implicit request =>
    val versions = versionsStr.split(',')
    val v1 = versions.head
    val v2 = versions.tail.head
    val v1Opt = if (v1.nonEmpty) Some(v1) else None
    val v2Opt = if (v2.nonEmpty) Some(v2) else None
    // convert list to use shared IcdVersion class
    val list = db.versionManager.diff(subsystem, v1Opt, v2Opt).map(getDiffInfo)
    Ok(Json.toJson(list))
  }

}
