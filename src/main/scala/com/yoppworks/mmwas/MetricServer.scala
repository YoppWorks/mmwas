package com.yoppworks.mmwas

import java.io.StringWriter

import scala.concurrent.{ExecutionContext, Future}
import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Source}
import com.yoppworks.mmwas.MusicMaker._
import io.prometheus.client.{CollectorRegistry, Counter, Gauge, Histogram}
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.hotspot.DefaultExports


/** Unit Tests For HttpServer */
case class MetricServer(samples: Source[MusicMakerMessage, NotUsed], listenPort: Int) {

  implicit val system: ActorSystem = ActorSystem("MusicMaker-Http-Server")
  implicit val mat: ActorMaterializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val ec: ExecutionContext = system.dispatcher

  private val metricsRegistry = CollectorRegistry.defaultRegistry

  DefaultExports.initialize()

  private val messages =
    Counter
      .build("mmwas_messages", "Total number of messages processed")
      .register(metricsRegistry)

  private val notes =
    Counter
      .build("mmwas_notes", "Total number of notes played")
      .register(metricsRegistry)

  private val pitches =
    Gauge
      .build("mmwas_pitches", "Distribution of note pitches")
      .register(metricsRegistry)

  private val velocities = Histogram
    .build("mmwas_velocities", "Distribution of note velocities")
    .linearBuckets(0.0, 16.0, 8)
    .register(metricsRegistry)

  private val channels = Histogram
    .build("mmwas_channels", "Distribution of note channels")
    .linearBuckets(0.0, 1.0, 16)
    .register(metricsRegistry)

  private val instruments = Histogram
    .build("mmwas_instruments", "Distribution of instruments used")
    .linearBuckets(0.0, 16.0, 8)
    .register(metricsRegistry)

  private val tempos = Histogram
    .build("mmwas_tempos", "Distribution of tempo")
    .linearBuckets(100000, 100000, 10)
    .register(metricsRegistry)

  private val pitchBends = Histogram
    .build("mmwas_pitch_bends", "Distribution of pitch bend messages")
    .linearBuckets(0.0, 8192, 2)
    .register(metricsRegistry)

  private val pressures = Histogram
    .build("mmwas_pressures", "Distribution of polyphonic pressures")
    .linearBuckets(0.0, 16.0, 8)
    .register(metricsRegistry)

  private val durations = Histogram
    .build("mmwas_durations", "Summary of duration of messages")
    .linearBuckets(0.0, 100.0, 10)
    .register(metricsRegistry)

  private var lastNoteTime: Long = 0
  private val nanosPerMilli: Double = 1000000


  private val terminus: Flow[MusicMakerMessage, NotUsed, NotUsed] = {
    Flow.fromFunction { message: MusicMakerMessage =>
      message match {
        case InertMusicMakerMessage =>
          messages.inc()
        case StartPlaying =>
          messages.inc()
          lastNoteTime = System.nanoTime()
        case StopPlaying =>
          messages.inc()
        case ChangeInstrument(channel: Int, instrument: Int) =>
          messages.inc()
          channels.observe(channel.doubleValue())
          instruments.observe(instrument.doubleValue())
        case PlayNote(channel, pitch, velocity, _) =>
          messages.inc()
          notes.inc()
          pitches.set(pitch.doubleValue())
          velocities.observe(velocity.doubleValue())
          channels.observe(channel.doubleValue())
          val thisNoteTime = System.nanoTime()
          durations.observe((thisNoteTime - lastNoteTime) / nanosPerMilli)
          lastNoteTime = thisNoteTime
        case StopLastNote(channel: Int) =>
          messages.inc()
          channels.observe(channel.doubleValue())
        case StopNote(channel: Int, _: Int, _: Int) =>
          messages.inc()
          channels.observe(channel.doubleValue())
        case SetTempo(microsecondsPerBeat: Int) =>
          messages.inc()
          tempos.observe(microsecondsPerBeat.doubleValue())
        case IgnoreNotes =>
          messages.inc()
        case ResumeNotes =>
          messages.inc()
        case SetPitchBend(channel: Int, pitchBend: Int) =>
          messages.inc()
          channels.observe(channel.doubleValue())
          pitchBends.observe(pitchBend.doubleValue())
        case SetPolyPressure(channel: Int, _: Int, pressure: Int) =>
          messages.inc()
          channels.observe(channel.doubleValue())
          pressures.observe(pressure.doubleValue())
      }
      NotUsed
    }
  }

  samples
    .via(terminus)
    .filter { _: NotUsed =>
      (messages.get().toLong % 1000) == 0
    }
    .runForeach { _ =>
      println(
        s"""
          |Metrics:
          |    Number of messages = ${messages.get()}
          |Number of notes played = ${notes.get()}
          |   Current pitch value = ${pitches.get()}
          | Velocities (volumes)  = ${velocities.collect()}
        """.stripMargin)
    }

  def getMetrics: String = {
    val writer = new StringWriter
    TextFormat.write004(writer, this.metricsRegistry.metricFamilySamples)
    writer.flush()
    val response = writer.toString
    writer.close()
    response
  }

  val route: Route = {
    get {
      pathEndOrSingleSlash {
        complete(StatusCodes.OK →
          HttpEntity(ContentTypes.`text/html(UTF-8)`,
            """<html><body><p>
              |Music Maker Metrics Exporter:
              |<a href="/metrics">/metrics</a>
              |</p></body></html>
              |""".stripMargin
          ))
      } ~ path(pm = "metrics") {
        complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, getMetrics))
      } ~ {
        reject
      }
    }
  }

  val bindingFuture: Future[Http.ServerBinding] = Http().bindAndHandle(
    route, interface = "localhost", port = listenPort
  )

  def terminate(): Unit = {
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
