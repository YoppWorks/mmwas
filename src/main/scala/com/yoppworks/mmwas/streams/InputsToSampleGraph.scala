package com.yoppworks.mmwas.streams

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.{Flow, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import com.yoppworks.mmwas.devices.Sample

object InputsToSampleGraph {
  def apply(
    volumes: Source[Long,NotUsed],
    temperatures: Source[Double,NotUsed],
    potentials: Source[Long,NotUsed]
  )(implicit mat: Materializer): Flow[Long, Sample, NotUsed] = {
    Flow.fromGraph[Long,Sample,NotUsed](
      new InputsToSampleGraph(volumes, temperatures, potentials)
    )
  }
}

/** Unit Tests For InputsToSampleGraph */
class InputsToSampleGraph(
  volumes: Source[Long,NotUsed],
  temperatures: Source[Double,NotUsed],
  potentials: Source[Long,NotUsed]
)(implicit mat: Materializer)
  extends GraphStage[FlowShape[Long, Sample]] {
  
  val pitchesPort: Inlet[Long] = Inlet[Long]("pitches.in")
  val samplesPort: Outlet[Sample] = Outlet[Sample]("samples.out")
  override val shape: FlowShape[Long, Sample] =
    FlowShape(pitchesPort, samplesPort)
  
  
  def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
    new GraphStageLogic(shape) {
      var lastTemperature: Double = 0.0
      var lastPotential: Long = 0L
      var lastVolume: Long = 0L

      temperatures.runForeach(t ⇒ lastTemperature = t)
      potentials.runForeach(p ⇒ lastPotential = p)
      volumes.runForeach( v ⇒ lastVolume = v)
  
      setHandler(pitchesPort, new InHandler {
        def onPush(): Unit = {
          val pitch = grab(pitchesPort)
          val sample = Sample(pitch, lastVolume, lastTemperature, lastPotential)
          push(samplesPort, sample)
        }
      })
      
      setHandler(samplesPort, new OutHandler {
        override def onPull() : Unit = {
          pull(pitchesPort)
        }
      })
    }
  }
  override def toString = "InputsToSampleGraph"
}


