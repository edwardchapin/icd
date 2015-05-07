package csw.services.icd.db

import com.mongodb.casbah.MongoDB
import com.mongodb.casbah.commons.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import com.typesafe.config.{ ConfigFactory, Config }
import csw.services.icd.StdName._
import csw.services.icd.model._
import scala.language.implicitConversions

object IcdDbQuery {
  // Set of standard ICD model parts: icd, component, publish, subscribe, command
  val stdSet = stdNames.map(_.modelBaseName).toSet

  // True if the named collection represents an ICD model (has one of the standard names)
  def isStdSet(name: String): Boolean =
    stdSet.exists(s ⇒ name.endsWith(s".$s"))

  // for working with dot separated paths
  case class IcdPath(path: String) {
    lazy val parts = path.split("\\.").toList

    // The common path for an assembly, HCD, sequencer, etc.
    lazy val component = parts.dropRight(1).mkString(".")

    // The top level ICD collection name
    lazy val icd = parts.head
  }

  // Contains db collection names related to an ICD
  case class IcdEntry(name: String, icd: Option[String], component: Option[String],
                      publish: Option[String], subscribe: Option[String], command: Option[String])

  // Types of published items
  sealed trait PublishType

  case object Telemetry extends PublishType

  case object Events extends PublishType

  case object EventStreams extends PublishType

  case object Alarms extends PublishType

  case object Health extends PublishType

  /**
   * Describes a published item
   * @param publishType one of Telemetry, Events, Alarms, etc.
   * @param name the name of the item being published
   * @param description description of the published item
   */
  case class Published(publishType: PublishType, name: String, description: String)

  /**
   * Describes a published item along with the component that publishes it
   * @param componentName the publishing component
   * @param prefix the component's prefix
   * @param item description of the published item
   */
  case class PublishedItem(componentName: String, prefix: String, item: Published)

  /**
   * Describes what values a component publishes
   * @param componentName component (HCD, assembly, ...) name
   * @param prefix component prefix
   * @param publishes list of names (without prefix) of published items (telemetry, events, alarms, etc.)
   */
  case class PublishInfo(componentName: String, prefix: String, publishes: List[Published])

  /**
   * Describes what values a component subscribes to
   * @param componentName component (HCD, assembly, ...) name
   * @param subscribesTo list of types and names (with prefix) of items the component subscribes to
   */
  case class SubscribeInfo(componentName: String, subscribesTo: List[Subscribed])

  /**
   * Describes a subscription
   *
   * @param componentName the name of the component that subscribes to the item
   * @param subscribeType one of Telemetry, Events, Alarms, etc.
   * @param name the name of the item being subscribed to
   * @param subsystem the subsystem to which the named item belongs
   */
  case class Subscribed(componentName: String, subscribeType: PublishType, name: String, subsystem: String)

  implicit def toDbObject(query: (String, Any)): DBObject = MongoDBObject(query)
}

/**
 * Support for querying the ICD database
 */
case class IcdDbQuery(db: MongoDB) {

  import IcdDbQuery._

  // Returns a list of IcdEntry for the ICDs (based on the collection names)
  // (XXX Should the return value be cached?)
  private def getEntries: List[IcdEntry] = {
    val paths = db.collectionNames().filter(isStdSet).map(IcdPath).toList
    val compMap = paths.map(p ⇒ (p.component, paths.filter(_.component == p.component).map(_.path))).toMap
    val entries = compMap.keys.map(key ⇒ getEntry(key, compMap(key))).toList
    entries.sortBy(entry ⇒ (IcdPath(entry.name).parts.length, entry.name))
  }

  // Returns an IcdEntry for the given collection path
  private def getEntry(name: String, paths: List[String]): IcdEntry = {
    IcdEntry(name = name,
      icd = paths.find(_.endsWith(".icd")),
      component = paths.find(_.endsWith(".component")),
      publish = paths.find(_.endsWith(".publish")),
      subscribe = paths.find(_.endsWith(".subscribe")),
      command = paths.find(_.endsWith(".command")))

  }

  // Gets a Config object from a JSON string
  private def getConfig(json: String): Config = {
    ConfigFactory.parseString(json)
  }

  // --- Components ---

  // Parses the given json and returns a componnet model object
  private def jsonToComponentModel(json: String): ComponentModel = {
    ComponentModel(getConfig(json))
  }

  // Returns an IcdEntry object for the given component name, if found
  private def entryForComponentName(name: String): Option[IcdEntry] = {
    val list = for (entry ← getEntries if entry.component.isDefined) yield {
      val coll = db(entry.component.get)
      val data = coll.findOne("name" -> name)
      if (data.isDefined) Some(entry) else None
    }
    list.flatten.headOption
  }

  /**
   * Returns a list of component model objects, one for each component ICD matching the given condition in the database
   * @param query restricts the components returned (a MongoDBObject, for example)
   */
  def queryComponents(query: DBObject): List[ComponentModel] = {
    val list = for (entry ← getEntries if entry.component.isDefined) yield {
      val coll = db(entry.component.get)
      val data = coll.findOne(query)
      if (data.isDefined) Some(jsonToComponentModel(data.get.toString)) else None
    }
    list.flatten
  }

  /**
   * Returns a list of component model objects, one for each component ICD of the given type in the database
   * @param componentType restricts the type of components returned (one of: Assembly, HCD, Sequencer, etc.)
   */
  def getComponents(componentType: String): List[ComponentModel] =
    queryComponents("componentType" -> componentType)

  /**
   * Returns a list of all component model objects, one for each component ICD in the database
   */
  def getComponents: List[ComponentModel] = {
    for (entry ← getEntries if entry.component.isDefined)
      yield jsonToComponentModel(db(entry.component.get).head.toString)
  }

  /**
   * Returns a list of all the component names in the DB
   */
  def getComponentNames: List[String] = getComponents.map(_.name)

  /**
   * Returns a list of all the subcomponent names in the DB belonging to the given ICD
   */
  def getComponentNames(icdName: String): List[String] = {
    db.collectionNames()
      .filter(_.endsWith(componentFileNames.modelBaseName))
      .map(IcdPath)
      .filter(p ⇒ p.icd == icdName && p.parts.length > 2)
      .map(_.path)
      .map(db(_).head.toString)
      .map(jsonToComponentModel(_).name)
      .toList
      .sorted
  }

  /**
   * Returns a list of all the assembly ICDs in the database
   */
  def getAssemblyNames: List[String] = getComponents("Assembly").map(_.name)

  /**
   * Returns a list of all the assembly ICDs in the database
   */
  def getHcdNames: List[String] = getComponents("HCD").map(_.name)

  /**
   * Returns a list of all top level ICDs in the database
   */
  def getIcdNames: List[String] = {
    // Get list of top level collection names, then get the component name for each, if defined
    val result = for (icdColl ← db.collectionNames().filter(isStdSet).map(IcdPath).map(_.icd).toList) yield {
      val coll = db(s"$icdColl.${componentFileNames.modelBaseName}")
      val data = coll.headOption
      if (data.isDefined) Some(jsonToComponentModel(data.get.toString).name) else None
    }
    result.flatten
  }

  // --- Get model objects, given a component name ---

  /**
   * Returns the model object for the component with the given name
   */
  def getComponentModel(name: String): Option[ComponentModel] = {
    queryComponents("name" -> name).headOption
  }

  /**
   * Returns an object describing the "commands" defined for the named component
   */
  def getCommandModel(name: String): Option[CommandModel] = {
    for (entry ← entryForComponentName(name) if entry.command.isDefined)
      yield CommandModel(getConfig(db(entry.command.get).head.toString))
  }

  /**
   * Returns an object describing the items published by the named component
   */
  def getPublishModel(name: String): Option[PublishModel] = {
    for (entry ← entryForComponentName(name) if entry.publish.isDefined)
      yield PublishModel(getConfig(db(entry.publish.get).head.toString))
  }

  /**
   * Returns an object describing the items subscribed to by the named component
   */
  def getSubscribeModel(name: String): Option[SubscribeModel] = {
    for (entry ← entryForComponentName(name) if entry.subscribe.isDefined)
      yield SubscribeModel(getConfig(db(entry.subscribe.get).head.toString))
  }

  /**
   * Returns an object describing the ICD for the named component
   */
  def getIcdModel(name: String): Option[IcdModel] = {
    for (entry ← entryForComponentName(name) if entry.icd.isDefined)
      yield IcdModel(getConfig(db(entry.icd.get).head.toString))
  }

  // ---

  /**
   * Returns a list of ICD models for the given component name,
   * based on the data in the database.
   * The list includes the ICD models for the component's ICD followed
   * by any ICD models for components that were defined in subdirectories
   * in the original files that were ingested into the database
   * (In this case the definitions are stored in sub-collections in the DB).
   */
  def getModels(componentName: String): List[IcdModels] = {
    // Holds all the model classes associated with a single ICD entry.
    case class Models(entry: IcdEntry) extends IcdModels {
      override val icdModel = entry.icd.map(s ⇒ IcdModel(getConfig(db(s).head.toString)))
      override val publishModel = entry.publish.map(s ⇒ PublishModel(getConfig(db(s).head.toString)))
      override val subscribeModel = entry.subscribe.map(s ⇒ SubscribeModel(getConfig(db(s).head.toString)))
      override val commandModel = entry.command.map(s ⇒ CommandModel(getConfig(db(s).head.toString)))
      override val componentModel = entry.component.map(s ⇒ ComponentModel(getConfig(db(s).head.toString)))
    }

    val compEntry = entryForComponentName(componentName)
    if (compEntry.isDefined) {
      // Get the prefix for the related db sub-collections
      val prefix = compEntry.get.name + "."
      val list = for (entry ← getEntries if entry.name.startsWith(prefix)) yield new Models(entry)
      Models(compEntry.get) :: list
    } else Nil
  }

  /**
   * Deletes the given component hierarchy. Use with caution!
   */
  def dropComponent(name: String): Unit = {
    val compEntry = entryForComponentName(name)
    if (compEntry.isDefined) {
      // Get the prefix for the related db sub-collections
      val topLevelPrefix = compEntry.get.name + "."
      val list = for (entry ← getEntries if entry.name.startsWith(topLevelPrefix)) yield entry.name
      (compEntry.get.name :: list).foreach { prefix ⇒
        stdSet.foreach { s ⇒
          val collName = s"$prefix.$s"
          if (db.collectionExists(collName)) db(collName).drop()
        }
      }
    }
  }

  /**
   * Returns a list of items published by the given component
   * @param name the component name
   */
  def getPublished(name: String): List[Published] = {
    getPublishModel(name) match {
      case Some(publishModel) ⇒
        List(publishModel.telemetryList.map(i ⇒ Published(Telemetry, i.name, i.description)),
          publishModel.eventList.map(i ⇒ Published(Events, i.name, i.description)),
          publishModel.eventStreamList.map(i ⇒ Published(EventStreams, i.name, i.description)),
          publishModel.alarmList.map(i ⇒ Published(Alarms, i.name, i.description)),
          publishModel.healthList.map(i ⇒ Published(Health, i.name, i.description))).flatten
      case None ⇒ Nil
    }
  }

  /**
   * Returns a list describing what each component publishes
   */
  def getPublishInfo: List[PublishInfo] = {
    def getPublishInfo(compName: String, prefix: String): PublishInfo =
      PublishInfo(compName, prefix, getPublished(compName))

    getComponents.map(c ⇒ getPublishInfo(c.name, c.prefix))
  }

  /**
   * Returns a list describing which components publish the given value.
   * @param path full path name of value (prefix + name)
   */
  def publishes(path: String): List[PublishedItem] = {
    for {
      i ← getPublishInfo
      p ← i.publishes.filter(p ⇒ s"${i.prefix}.${p.name}" == path)
    } yield PublishedItem(i.componentName, i.prefix, p)
  }

  /**
   * Returns a list of items the given component subscribes to
   * @param name the component name
   */
  def getSubscribedTo(name: String): List[Subscribed] = {
    getSubscribeModel(name) match {
      case Some(subscribeModel) ⇒
        List(subscribeModel.telemetryList.map(i ⇒ Subscribed(name, Telemetry, i.name, i.subsystem)),
          subscribeModel.eventList.map(i ⇒ Subscribed(name, Events, i.name, i.subsystem)),
          subscribeModel.eventStreamList.map(i ⇒ Subscribed(name, EventStreams, i.name, i.subsystem)),
          subscribeModel.alarmList.map(i ⇒ Subscribed(name, Alarms, i.name, i.subsystem)),
          subscribeModel.healthList.map(i ⇒ Subscribed(name, Health, i.name, i.subsystem))).flatten
      case None ⇒ Nil
    }
  }

  /**
   * Returns an object describing what the given component subscribes to
   */
  def getSubscribeInfo(name: String): SubscribeInfo = SubscribeInfo(name, getSubscribedTo(name))

  /**
   * Returns a list describing what each component subscribes to
   */
  def getSubscribeInfo: List[SubscribeInfo] = {
    getComponents.map(c ⇒ getSubscribeInfo(c.name))
  }

  /**
   * Returns a list describing the components that subscribe to the given value.
   * @param path full path name of value (prefix + name)
   */
  def subscribes(path: String): List[Subscribed] = {
    for {
      i ← getSubscribeInfo
      s ← i.subscribesTo.filter(_.name == path)
    } yield s
  }
}
