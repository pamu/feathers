package com.haskworks.feathers

import scala.concurrent.{ExecutionContext, Future}

class Feather[A] private(code: => A) {
  def run()(implicit ec: ExecutionContext): Future[A] = Future(code)
}

object Feather {
  def apply[A](code: => A): Feather[A] = new Feather[A](code)
}