import org.scalatest.{FlatSpec, Matchers}

/*
 * Copyright (c) 2016 Pamu Nagarjuna (http://pamu.github.io).
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

/**
  * Created by pnagarjuna on 11/11/16.
  */

class SampleTest extends FlatSpec with Matchers {
  "a" should "be equal to 1" in {
    val a = 1
    a should be (1)
  }
}
