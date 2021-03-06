package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.EventModel
import reactivemongo.api.bson._

/**
 * See resources/event-schema.conf
 */
object EventModelBsonParser {

  def apply(doc: BSONDocument): EventModel =
    EventModel(
      name = doc.getAsOpt[String]("name").get,
      description = doc.getAsOpt[String]("description").map(HtmlMarkup.gfmToHtml).getOrElse(""),
      requirements = doc.getAsOpt[Array[String]]("requirements").map(_.toList).getOrElse(Nil),
      maybeMaxRate = doc.getAsOpt[BSONNumberLike]("maxRate").map(_.toDouble.getOrElse(1.0)),
      archive = doc.getAsOpt[Boolean]("archive").getOrElse(false),
      archiveDuration = doc.getAsOpt[String]("archiveDuration").getOrElse(""),
      attributesList =
        for (subDoc <- doc.getAsOpt[Array[BSONDocument]]("attributes").map(_.toList).getOrElse(Nil))
          yield AttributeModelBsonParser(subDoc),

    )
}
