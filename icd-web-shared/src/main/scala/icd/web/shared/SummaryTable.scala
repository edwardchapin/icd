package icd.web.shared

import scalatags.Text
import scalatags.Text.all._
import icd.web.shared.ComponentInfo.{Alarms, CurrentStates, Events, ObserveEvents}
import icd.web.shared.IcdModels.{ComponentModel, EventModel}
import Headings.idFor

//noinspection DuplicatedCode
object SummaryTable {

  /**
   * Displays a summary of the events published and commands received by the subsystem
   *
   * @param subsystemInfo subsystem to use
   * @param maybeTargetSv optional target subsystem and version
   * @param infoList      list of component info
   * @param nh            used for numbered headings and TOC, if needed
   * @return the HTML
   */
  def displaySummary(
      subsystemInfo: SubsystemInfo,
      maybeTargetSv: Option[SubsystemWithVersion],
      infoList: List[ComponentInfo],
      nh: Headings = new HtmlHeadings
  ): Text.TypedTag[String] = {
    import SummaryInfo._

    val isIcd               = maybeTargetSv.isDefined
    val componentPart       = subsystemInfo.sv.maybeComponent.map("." + _).getOrElse("")
    val targetComponentPart = maybeTargetSv.flatMap(_.maybeComponent.map("." + _)).getOrElse("")
    val sourceStr           = subsystemInfo.sv.subsystem + componentPart

    def firstParagraph(s: String): String = {
      val i = s.indexOf("</p>")
      if (i == -1) s else s.substring(0, i + 4)
    }

    // Displays a summary for published items of a given event type or commands received.
    def publishedSummaryMarkup(
        itemType: String,
        list: List[PublishedItem],
        heading: String,
        prep: String
    ): Text.TypedTag[String] = {
      val (action, publisher, subscribers) = heading.toLowerCase() match {
        case "published by" => ("publishes", "Publisher", "Subscribers")
        case "received by"  => ("receives", "Receiver", "Senders")
      }

      val targetStr = if (maybeTargetSv.isDefined) s" $prep ${maybeTargetSv.get.subsystem}$targetComponentPart" else ""

      def linkToSubscriber(subscriber: ComponentModel) = {
        if ((isIcd && subscriber.subsystem == maybeTargetSv.get.subsystem) || subscriber.subsystem == subsystemInfo.sv.subsystem)
          span(a(href := s"#${subscriber.component}")(subscriber.component), " ")
        else
          span(s"${subscriber.subsystem}.${subscriber.component}", " ")
      }

      val showYearlyAccum = !isIcd && itemType.endsWith("Events")

      def totalArchiveSpace(): Text.TypedTag[String] = {
        val sumTotal = EventModel.getTotalArchiveSpace(list.map(_.item.asInstanceOf[EventModel]))
        if (sumTotal.nonEmpty)
          p(strong(s"Total yearly space required for archiving events published by $sourceStr: $sumTotal"))
        else span()
      }

      if (list.isEmpty) div()
      else {
        div(
          nh.H3(s"$itemType $heading $sourceStr$targetStr"),
          table(
            thead(
              tr(
                th(publisher),
                th(subscribers),
                th("Prefix"),
                th("Name"),
                if (showYearlyAccum) th("Yearly", br, "Accum.") else span(),
                th("Description")
              )
            ),
            tbody(
              for {
                info <- list
              } yield {
                tr(
                  td(p(a(href := s"#${info.publisher.component}")(info.publisher.component))),
                  td(p(info.subscribers.map(linkToSubscriber))),
                  td(p(a(href := s"#${info.publisher.component}")(info.publisher.prefix))),
                  td(
                    p(
                      a(
                        href := s"#${idFor(info.publisher.component, action, itemType, info.publisher.subsystem, info.publisher.component, info.item.name)}"
                      )(info.item.name)
                    )
                  ),
                  if (showYearlyAccum) {
                    val yearlyAccum =
                      if (itemType.endsWith("Events")) info.item.asInstanceOf[EventModel].totalArchiveSpacePerYear else ""
                    td(yearlyAccum)
                  } else span(),
                  td(raw(firstParagraph(info.item.description)))
                )
              }
            )
          ),
          if (showYearlyAccum) totalArchiveSpace() else span()
        )
      }
    }

    // Displays a summary for subscribed items of a given event type or commands sent.
    def subscribedSummaryMarkup(
        itemType: String,
        list: List[SubscribedItem],
        heading: String,
        prep: String
    ): Text.TypedTag[String] = {

      // For ICDs, display subscribed items from the publisher's point of view
      def publisherView(): Text.TypedTag[String] = {
        val (action, publisher, subscriber) = heading.toLowerCase() match {
          case "published by" => ("publishes", "Publisher", "Subscriber")
          case "received by"  => ("receives", "Receiver", "Senders")
        }

        val target = maybeTargetSv.get.subsystem

        div(
          nh.H3(s"$itemType $heading $target$targetComponentPart $prep $sourceStr"),
          table(
            thead(
              tr(
                th(publisher),
                th(subscriber),
                th("Prefix"),
                th("Name"),
                th("Description")
              )
            ),
            tbody(
              for {
                info <- list
              } yield {
                // If this is an ICD or the publisher is in the same subsystem, we can link to it, since it is in this doc
                val prefixItem = info.maybePublisher match {
                  case Some(componentModel) => span(componentModel.prefix)
                  case None                 => span()
                }
                val description = info.maybeWarning match {
                  case Some(msg) => p(em("Warning: ", msg))
                  case None      => raw(firstParagraph(info.item.description))
                }

                // ICDs contain both subsystems, so we can link to them
                tr(
                  td(p(a(href := s"#${info.publisherComponent}")(info.publisherComponent))),
                  td(p(a(href := s"#${info.subscriber.component}")(info.subscriber.component))),
                  td(p(a(href := s"#${info.publisherComponent}")(prefixItem))),
                  td(
                    p(
                      a(
                        href := s"#${idFor(info.publisherComponent, action, itemType, info.publisherSubsystem, info.publisherComponent, info.item.name)}"
                      )(info.item.name)
                    )
                  ),
                  td(description)
                )
              }
            )
          )
        )
      }

      // For APIs, display the list of subscribed items
      def subscriberView(): Text.TypedTag[String] = {
        val (subscribes, subscriber, publisher) = heading.toLowerCase() match {
          case "subscribed to by" => ("subscribes", "Subscriber", "Publisher")
          case "sent by"          => ("sends", "Sender", "Receiver")
        }
        val targetStr = if (maybeTargetSv.isDefined) s" $prep ${maybeTargetSv.get.subsystem}$targetComponentPart" else ""
        div(
          nh.H3(s"$itemType $heading $sourceStr$targetStr"),
          table(
            thead(
              tr(
                th(publisher),
                th(subscriber),
                th("Prefix"),
                th("Name"),
                th("Description")
              )
            ),
            tbody(
              for {
                info <- list
              } yield {
                val prefixItem = info.maybePublisher match {
                  case Some(componentModel) => span(componentModel.prefix)
                  case None                 => span()
                }

                val publisherComponent =
                  if (info.publisherSubsystem == info.subscriber.subsystem)
                    a(href := s"#${info.publisherComponent}")(info.publisherComponent)
                  // XXX TODO FIXME: Make link in web app for components in other subsystems also!
                  else span(s"${info.publisherSubsystem}.${info.publisherComponent}")

                val publisherPrefix =
                  if (info.publisherSubsystem == info.subscriber.subsystem)
                    a(href := s"#${info.publisherComponent}")(prefixItem)
                  else span(prefixItem)

                val description = info.maybeWarning match {
                  case Some(msg) => p(em("Warning: ", msg))
                  case None      => raw(firstParagraph(info.item.description))
                }

                tr(
                  td(p(publisherComponent)),
                  td(p(a(href := s"#${info.subscriber.component}")(info.subscriber.component))),
                  td(p(publisherPrefix)),
                  td(
                    p(
                      a(
                        href := s"#${idFor(info.subscriber.component, subscribes, itemType, info.publisherSubsystem, info.publisherComponent, info.item.name)}"
                      )(info.item.name)
                    )
                  ),
                  td(description)
                )
              }
            )
          )
        )
      }

      if (list.isEmpty)
        div()
      else if (isIcd)
        publisherView()
      else subscriberView()
    }

    def publishedSummary(): Text.TypedTag[String] = {
      val publishedEvents = for {
        info  <- infoList
        pub   <- info.publishes.toList
        event <- pub.eventList
      } yield PublishedItem(info.componentModel, event.eventModel, event.subscribers.map(_.componentModel))

      val publishedObserveEvents = for {
        info  <- infoList
        pub   <- info.publishes.toList
        event <- pub.observeEventList
      } yield PublishedItem(info.componentModel, event.eventModel, event.subscribers.map(_.componentModel))

      val publishedCurrentStates = for {
        info  <- infoList
        pub   <- info.publishes.toList
        event <- pub.currentStateList
      } yield PublishedItem(info.componentModel, event.eventModel, event.subscribers.map(_.componentModel))

      val publishedAlarms = for {
        info  <- infoList
        pub   <- info.publishes.toList
        event <- pub.alarmList
      } yield PublishedItem(info.componentModel, event.alarmModel, event.subscribers.map(_.componentModel))

      val receivedCommands = for {
        info     <- infoList
        commands <- info.commands.toList
        command  <- commands.commandsReceived
      } yield PublishedItem(info.componentModel, command.receiveCommandModel, command.senders)

      div(
        publishedSummaryMarkup("Events", publishedEvents, "Published by", "for"),
        publishedSummaryMarkup("Observe Events", publishedObserveEvents, "Published by", "for"),
        publishedSummaryMarkup("Current States", publishedCurrentStates, "Published by", "for"),
        publishedSummaryMarkup("Alarms", publishedAlarms, "Published by", "for"),
        publishedSummaryMarkup("Commands", receivedCommands, "Received by", "from")
      )
    }

    def subscribedSummary(): Text.TypedTag[String] = {
      // For subscribed items and sent commands, the info from the other subsystem might not be available
      val allSubscribed = for {
        info  <- infoList
        sub   <- info.subscribes.toList
        event <- sub.subscribeInfo
      } yield (
        event.itemType,
        SubscribedItem(
          event.subscribeModelInfo.subsystem,
          event.subscribeModelInfo.component,
          event.publisher,
          event.warning,
          info.componentModel,
          OptionalNameDesc(event.subscribeModelInfo.name, event.eventModel)
        )
      )
      val subscribedEvents        = allSubscribed.filter(_._1 == Events).map(_._2)
      val subscribedObserveEvents = allSubscribed.filter(_._1 == ObserveEvents).map(_._2)
      val subscribedCurrentStates = allSubscribed.filter(_._1 == CurrentStates).map(_._2)
      val subscribedAlarms        = allSubscribed.filter(_._1 == Alarms).map(_._2)

      val sentCommands = for {
        info     <- infoList
        commands <- info.commands.toList
        command  <- commands.commandsSent
      } yield SubscribedItem(
        command.subsystem,
        command.component,
        command.receiver,
        command.warning,
        info.componentModel,
        OptionalNameDesc(command.name, command.receiveCommandModel)
      )
      div(
        if (isIcd)
          div(
            subscribedSummaryMarkup("Events", subscribedEvents, "Published by", "for"),
            subscribedSummaryMarkup("Observe Events", subscribedObserveEvents, "Published by", "for"),
            subscribedSummaryMarkup("Current States", subscribedCurrentStates, "Published by", "for"),
            subscribedSummaryMarkup("Alarms", subscribedAlarms, "Published by", "for"),
            subscribedSummaryMarkup("Commands", sentCommands, "Received by", "from")
          )
        else
          div(
            subscribedSummaryMarkup("Events", subscribedEvents, "Subscribed to by", "from"),
            subscribedSummaryMarkup("Observe Events", subscribedObserveEvents, "Subscribed to by", "from"),
            subscribedSummaryMarkup("Current States", subscribedCurrentStates, "Subscribed to by", "from"),
            subscribedSummaryMarkup("Alarms", subscribedAlarms, "Subscribed to by", "from"),
            subscribedSummaryMarkup("Commands", sentCommands, "Sent by", "to")
          )
      )
    }

    div(
      nh.H2("Summary"),
      publishedSummary(),
      subscribedSummary()
    )
  }

}
