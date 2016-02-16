package icd.web.client

import org.scalajs.dom._

/**
 * Manages the View menu (XXX not currently used)
 */
case class ViewMenu(
    showVersionHistory: () ⇒ Unit
) extends Displayable {

  // Returns the HTML markup for the view menu item
  def markup(): Element = {
    import scalatags.JsDom.all._
    li(cls := "dropdown")(
      a(cls := "dropdown-toggle", "data-toggle".attr := "dropdown", "View", b(cls := "caret")),
      ul(cls := "dropdown-menu")(
        li(a(onclick := showVersionHistory)("Show Version History"))
      )
    ).render
  }
}
