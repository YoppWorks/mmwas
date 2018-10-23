package com.yoppworks.mmwas.streams

import akka.stream._
import akka.stream.stage._

case class SustainFlow[T](initial: T) extends GraphStage[FlowShape[T, T]] {
  val in: Inlet[T] = Inlet[T]("Sustain.in")
  val out: Outlet[T] = Outlet[T]("Sustain.out")

  override val shape: FlowShape[T,T]  = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private var currentValue: T = initial

    setHandlers(in, out, new InHandler with OutHandler {
      override def onPush(): Unit = {
        currentValue = grab(in)
        pull(in)
      }

      override def onPull(): Unit = {
        push(out, currentValue)
      }
    })

    override def preStart(): Unit = {
      pull(in)
    }
  }
}