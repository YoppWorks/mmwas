package com.yoppworks.mmwas.devices

import akka.NotUsed
import akka.actor.Cancellable
import akka.stream.Materializer
import akka.stream.scaladsl.Source

sealed trait PitchDevice extends DeviceSource[Int] {
  val deviceName: String= "Pitch"
}

case class MousePositionPitchDevice(
  input: ScreenInput, output: ScreenOutput
)(implicit mat: Materializer)
  extends PitchDevice {
  val maxValue: Int = output.getHeight
  val minValue: Int = 0
  private var lastValue: Int = 0
  private var canceler: Cancellable = Cancellable.alreadyCancelled
  override def dataSource : Source[Int, NotUsed] = {
    val (c, source) = input
      .mousePositionSource
      .map {
        case MouseRelocated(_, y) ⇒
          val fromBottom = maxValue - y
          val proportional = fromBottom * 100 / maxValue + 25
          Math.max(0, Math.min(proportional, 127))
      }.filterNot {
        case nextValue if nextValue == lastValue => true
        case nextValue =>
          lastValue = nextValue
          false
      }
      .preMaterialize()
    canceler = c
    source
  }

  override def unprovision(): Unit = {
    val _ = canceler.cancel()
  }
}


