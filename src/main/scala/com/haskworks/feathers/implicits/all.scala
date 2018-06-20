package com.haskworks.feathers.implicits

import cats.Monad
import com.haskworks.feathers.Feather

trait CatsMonadInstance {

  // TODO: implement the monad for feature
  implicit val featherMonad: Monad[Feather] = new Monad[Feather] {
    override def pure[A](x: A): Feather[A] = ???
    override def flatMap[A, B](fa: Feather[A])(f: A => Feather[B]): Feather[B] = ???
    override def tailRecM[A, B](a: A)(f: A => Feather[Either[A, B]]): Feather[B] = ???
  }

}

object all extends CatsMonadInstance
