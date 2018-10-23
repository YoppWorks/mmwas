package com.yoppworks.mmwas.devices

import java.awt._
import java.awt.event._

import com.yoppworks.mmwas.MakingMusicWithAkkaStreams.MakingMusicConfig
import javax.swing.BoxLayout
import javax.swing.JFrame
import javax.swing.JPanel

import scala.io.StdIn


case class PaneData(instrument: Int, note: Int)

class DrawPane(
  frame: JFrame,
  soundFont: String
) extends JPanel {

  private final val myFont: Font = new Font(Font.SANS_SERIF,Font.BOLD,48)

  setLayout(new BoxLayout(this,BoxLayout.Y_AXIS))

  var data: PaneData = PaneData(0,0)

  override def paintComponent(g: Graphics): Unit = {
    val (frameWidth, frameHeight) = {
      val size = frame.getSize
      size.width -> size.height
    }
    g.setPaintMode()
    g.setColor(Color.BLACK)
    g.fillRect(0, 0, frameWidth, frameHeight)
    g.setColor(Color.GREEN)
    g.setFont(myFont)
    g.drawString("Making Music With Akka Streams", 50, 50)
    g.drawString(s"instrument = ${data.instrument}", 50, 100)
    g.drawString(s"note = ${data.note}", 50, 150)
    g.drawString(s"sound font = $soundFont", 50, 200)
  }
}

case class ScreenOutput(
  config: MakingMusicConfig
) extends JFrame("Making Music With Akka Streams") {

  private val closeWindow = new WindowAdapter() {
    override def windowClosing(e : WindowEvent) : Unit = {
      e.getWindow.dispose()
    }
  }

  setExtendedState(Frame.MAXIMIZED_BOTH)
  setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)

  // setSize(this.getMaximumSize)
  setLayout(new BorderLayout)

  private val soundFont = config.soundFont
  private final val mb = new MenuBar
  private final val m1 = new Menu("File")
  private final val mi1 = new MenuItem("Exit")

  m1.add(mi1)
  setMenuBar(mb)
  mb.add(m1)
  this.addWindowListener(closeWindow)

  private final val drawPane = new DrawPane(this, soundFont)

  this.setContentPane(drawPane)
  pack()
  setVisible(true)
  toFront()

  def update(paneData: PaneData): Unit = {
    drawPane.data = paneData
    drawPane.repaint()
  }
}

object ScreenOutput {
  def main(args : Array[String]) : Unit = {
    val _ = new ScreenOutput(MakingMusicConfig())
    println("Press Enter to exit")
    StdIn.readLine() // let it run until user presses return
    ()
  }
}
