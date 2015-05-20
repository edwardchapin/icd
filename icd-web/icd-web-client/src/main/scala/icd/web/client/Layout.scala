package icd.web.client

import org.scalajs.dom._
import org.scalajs.dom.raw.Node

import scalacss.ScalatagsCss._
import scalatags.JsDom.all._

/**
 * Manages the main layout (below the navbar)
 */
case class Layout() extends Displayable {

  //Styles.render[TypedTag[HTMLStyleElement]], Styles.mainWrapper,
  val wrapper = div(Styles.wrapper).render

  override def markup(): Element = wrapper

  /**
   * Adds an HTML element to the layout.
   * @param node a scalatags node
   */
  def addItem(node: Node): Unit = {
    wrapper.appendChild(node)
  }
}