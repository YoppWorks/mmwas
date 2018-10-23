package com.yoppworks.mmwas

import java.util.concurrent.TimeUnit

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.io.StdIn
import akka.actor.ActorSystem
import akka.stream._
import com.reactific.mmwas.ComReactificMmwasInfo
import com.yoppworks.mmwas.devices._
import scopt.Read

object MakingMusicWithAkkaStreams {

  System.setProperty("pi4j.linking", "dynamic")

  implicit val system: ActorSystem = ActorSystem("MakingMusicWithAkkaStreams")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  sealed trait RunMode
  case object RandomMode extends RunMode
  case object ScreenMode extends RunMode
  case object GPIOMode extends RunMode
  
  case class MakingMusicConfig(
    version: String = "1.0",
    mode: RunMode = ScreenMode,
    httpPort: Int = 9191,
    soundFont: String = "",
    sampleRate: FiniteDuration = 1.millis,
    sustainRate: FiniteDuration = 1000.millis,
    eventLogging: Boolean = false,
    maxRandomNotes: Int = 1000
  )

  implicit val durationReader: Read[FiniteDuration] = new
      Read[FiniteDuration] {
    val arity : Int = 1
    val reads : String => FiniteDuration = { opt : String ⇒
      val d = Duration.apply(opt)
      require(d.isFinite, "infinite duration")
      FiniteDuration(d.toMicros, TimeUnit.MICROSECONDS)
    }
  }

  def main(args: Array[String]): Unit = {
    try {
      val makingMusicVersion = "v" + ComReactificMmwasInfo.version
        Option(System.getProperty("version")).getOrElse("Unknown")

      val parser =
        new scopt.OptionParser[ MakingMusicConfig ](programName = "Making Music With Akka Streams") {
          version(makingMusicVersion)
          head("Making Music With Akka Streams", makingMusicVersion)
          opt[String]('m', "mode").action { (x, c) ⇒
            val newMode = x.toLowerCase match {
              case "screen" ⇒ ScreenMode
              case "random" ⇒ RandomMode
              case "gpio" ⇒ GPIOMode
            }
            c.copy(mode = newMode)
          }
          opt[Int]('p', "http-port").action( (x, c) ⇒
            c.copy(httpPort = x)).text("Sets the metric server's HTTP port")
          opt[String]('f', "sound-font").action( (x,c) ⇒
            c.copy(soundFont = x))
            .text("Provides the name of the sound font 2 file to use")
          opt[FiniteDuration]('r', "sample-rate").action( (x,c) ⇒
            c.copy(sampleRate = x)).text("The sample rate for the theremin")
          opt[FiniteDuration]('s', "sustain-rate").action( (x,c) =>
            c.copy(sustainRate = x)).text("The rate at which sustaining notes are applied")
          opt[Boolean]('l', "event-logging").action( (x,c) =>
            c.copy(eventLogging = x)).text("Turn on logging of screen events")
          opt[Int]('m',"max-random-notes").action( (x,c) =>
            c.copy(maxRandomNotes = x)).text("Limit number of sequential random notes")
        }

      parser.
        parse(args, MakingMusicConfig()) match {
        case Some(config) ⇒
          parser.showHeader()
          run(config)
        case None ⇒
          println("Configuration parsing failed. Exiting.")
      }
    } finally {
      val _ = system.terminate()
    }
  }

  def run(config: MakingMusicConfig): Unit = {
    var deviceManager: Option[DeviceManager] = None
    var webServer: Option[MetricServer] = None
    try {
      config.mode match {
        case ScreenMode ⇒
          val so = ScreenOutput(config)
          val si = ScreenInput(so, config)
          val mm = MusicMachine(config, si, so, 0)
          val _ = mm.play()
          val source = mm.combinedMessages.async("akka.metrics-dispatcher")
          val ws = MetricServer(source, config.httpPort)
          webServer = Some(ws)
          mm.startDevices()
          println(s"Server online at http://localhost:${config.httpPort}")
          println(s"Press RETURN to stop...")
          StdIn.readLine() // let it run until user presses return
          mm.stopDevices()
          so.dispose()
        case RandomMode ⇒
          deviceManager = Some(MockDeviceManager(config))
          deviceManager.foreach { dm: DeviceManager ⇒
            dm.startDevices()
            dm.printDeviceInfo()
            dm.startDevices()
            println(s"Press RETURN to stop...")
            StdIn.readLine() // let it run until user presses return
            dm.stopDevices()
          }
        case GPIOMode ⇒
          deviceManager = Some(RaspiDeviceManager(config))
          deviceManager.foreach { dm: DeviceManager ⇒
            dm.startDevices()
            dm.printDeviceInfo()
            dm.startDevices()
            println(s"Press RETURN to stop...")
            StdIn.readLine() // let it run until user presses return
            dm.stopDevices()
          }
      }
    } finally {
      webServer.foreach(_.terminate())
      deviceManager.foreach(_.terminate())
      val _ = system.terminate()
    }
    println("Making Music With Akka Streams is done!")
  }
  
  /*

  def runLED(): Unit = {
    import LightEmittingDiode._
    val source: Source[LEDMessages, NotUsed] =
      Source.unfold[Long, LEDMessages](0) {
        case t: Long if t > 10000 ⇒ None
        case t: Long ⇒
          val time = Random.nextInt(225).toLong + 25L
          val isOn = Random.nextBoolean
          val msg = if (isOn) { LEDOn(time) } else { LEDOff(time) }
          Some((t + time, msg))
      }

    val (diode, future) = LightEmittingDiode.create(gpio, LEDPin)
    gpio.showProvisionedPins()
    LightEmittingDiode.run(diode, source)
    val _ = Await.result(future, 12.seconds)
  }
    */
  
}
