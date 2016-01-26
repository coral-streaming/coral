/*
 * Copyright 2016 Coral realtime streaming analytics (http://coral-streaming.github.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.coral.actors.transform

import java.util.concurrent.TimeUnit
import akka.actor.{Cancellable, Props}
import akka.event.slf4j.Logger
import io.coral.actors.CoralActor
import io.coral.utils.Utils
import org.json4s.JsonAST.JValue
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.uncommons.maths.random._
import scala.collection.mutable.{Stack => mStack}
import scala.concurrent.duration.Duration
import scala.util.matching.Regex
import scala.collection.mutable.{Map => mMap}

/**
 * An actor that can generate random data based on a definition in the
 * JSON constructor. The format object has the same format as the
 * expected output but with a sampling from a normal distribution,
 * a uniform distribution or a list of choices in it, as in the example
 * below. The field value contains the parameters of the distribution.
 * The rate sets the number of objects that it generates per second
 * and times sets the total number of objects that will be emitted.
 * Delay sets the time to wait before emitting the first object.
 *
 * {
 *   "format": {
 *     // Normal distribution with mu 100 and sigma 10
 *     "field1": "N(100, 10)",
 *     // choose randomly from a list of values
 *     "field2": "['a', 'b', 'c']",
 *     // uniform distribution with max = 100
 *     "field3": "U(100)"
 *  }, "timer": {
 *     // per second
 *     "rate": "10",
 *     // total number of items to generate
 *     "times": "100",
 *     // initial delay in seconds
 *     "delay": "10"
 *   }
 * }
 */
case class StartGenerator()
case class GenerateData()
case class StopGenerator()

object GeneratorActor {
	implicit val formats = org.json4s.DefaultFormats

	def getParams(json: JValue) = {
		for {
			// The structure of the object to emit
			format <- (json \ "params" \ "format").extractOpt[JObject]
			generators <- getGenerators(format)
			rate <- (json \ "params" \ "timer" \ "rate").extractOpt[Double]
			delay <- getDoubleValueOrZero(json \ "params" \ "timer" \ "delay")
			if rate >= 0
		} yield {
			val times: Option[Int] = getTimes(json \ "params" \ "timer" \ "times")
			(format, generators, rate, times, delay)
		}
	}

	/**
	 * Return a map of generators based on the input JSON format.
	 * @param format The JSON object which contains the generators, if any.
	 * @return A Map which points generator strings to their generators.
	 *         Example: "N(100, 2)" -> NormalGenerator(...)
	 *         Every time a "N(100, 2)" generator is encountered in the
	 *         format JSON, this generator is called.
	 */
	def getGenerators(format: JObject): Option[Map[String, Generator]] = {
		try {
			val values: mStack[JValue] = new mStack()
			val result = mMap.empty[String, Generator]

			values.pushAll(format.children)

			while (!values.isEmpty) {
				val next = values.pop

				next match {
					case JObject(j) =>
						// Nested object, add all children
						values.pushAll(j.children)
					case JString(s) =>
						val generator = try {
							s.substring(0, 2) match {
								case "N(" => // N(mu, sigma)
									new NormalGenerator(s)
								case "U(" => // U(max)
									new UniformGenerator(s)
								case "['" => // "['e1', 'e2', ...]"
									new ArrayChoiceGenerator(s)
								case _ =>
									// Assume that the string is a simple constant string
									new ConstantGenerator(s)
							}
						} catch {
							case _: Exception => new ConstantGenerator(s)
						}

						result.put(s, generator)
					case _ =>
				}
			}

			Some(result.toMap)
		} catch {
			case e: Exception =>
				Logger(getClass.getName).error(e.getMessage())
				Utils.stackTraceToString(e)
				None
		}
	}

	/**
	 * Returns the double value of a given JValue.
	 * in case it cannot be parsed or it is not a numeric value, return 0.
	 * The minimum value is 0.
	 */
	def getDoubleValueOrZero(value: JValue) = {
		val result = value match {
			case JInt(d) => d.toDouble
			case JDouble(d) => d
			case _ => 0
		}

		Some(result.max(0))
	}

	/**
	 * Same thing but then for integers.
	 */
	def getIntValueOrZero(value: JValue) = {
		getDoubleValueOrZero(value).map(_.toInt)
	}

	def getTimes(value: JValue) = {
		value.extractOpt[Int]
	}

	def apply(json: JValue): Option[Props] = {
		getParams(json).map(_ => Props(classOf[GeneratorActor], json))
	}
}

class GeneratorActor(json: JObject) extends CoralActor(json) {
	val (format, generators, rate, times, delay) = GeneratorActor.getParams(json).get
	// time to wait before ticking again in milliseconds
	def timerDuration: Double = 1000.0 / rate
	// The timestamp on which the generator was started
	var startTime: Long = _
	// The number of messages generated until now
	var count = 0
	// Whether or not the generator was started
	var started = false
	// time since last data was generated
	var previousTime: Long = _

	override def preStart() {
		super.preStart()
	}

	override def receiveExtra: Receive = {
		case StartGenerator() =>
			if (!times.isDefined || (times.isDefined && times.get > 0)) {
				startTime = System.currentTimeMillis

				log.info(s"Starting generator with an interval of $timerDuration milliseconds")

				if (delay > 0) {
					in(Duration((delay * 1000).toLong, TimeUnit.MILLISECONDS)) {
						self ! GenerateData()
						started = true
					}
				} else {
					self ! GenerateData()
					started = true
				}
			}
		case StopGenerator() =>
			if (started) {
				started = false
			}
		case GenerateData() =>
			generateData(format)
	}

	override def state = Map(
		("rate", render(rate)),
		("times", render(times)),
		("delay", render(delay)),
		("format", render(format)),
		("count", render(count))
	)

	/**
	 * Generate data based on a JObject format definition.
	 * The fields are maintained but replaced with values from
	 * a uniform, normal or discrete choice distribution.
	 * @param format The format JSON object to process
	 * @return A JSON object in the same format but with values
	 *         filled in.
	 */
	def generateData(format: JObject): JValue = {
		if (times.isDefined && count >= times.get) {
			self ! StopGenerator()
			JNothing
		} else {
			count += 1

			// This is done in a "string processing" mode because of performance reasons.
			// Not sure how much slower or faster merge and "~" will be, but assuming this is faster
			val template = pretty(render(format))

			val result = generators.keys.foldLeft(template)((acc, curr) => {
				val value = generators(curr).nextValue
				acc.replace("\"" + curr + "\"", value.toString)
			})

			timeNextGenerate()
			val parsed = parse(result).asInstanceOf[JObject]
			emit(parsed)
			parsed
		}
	}

	/**
	 * Calculate when the next generateData event should be fired.
	 * The GeneratorActor can be too fast in comparison to the "wall" time,
	 * or it can be too slow. In the case it is too slow (the actual rate of messages
	 * is lower than the given rate) there is really nothing that can be done.
	 * In the case it is too fast, slow down until we are in sync with the given
	 * rate again.
	 *
	 * This was not implemented using system.scheduler because it
	 * is too inacurrate and this gives us more control.
	 */
	def timeNextGenerate() {
		val actuallyProcessed = count
		val currentTime: Long = System.currentTimeMillis
		// The amount of time actually passed since the generator was started
		val actualPassedMillis = currentTime - startTime
		// The amount of messages that should have been processed
		val shouldHaveProcessed = (actualPassedMillis * (rate / 1000)).toInt
		// The difference between actual and expected number of messages
		val delta = actuallyProcessed - shouldHaveProcessed

		if (delta <= 0) {
			// When delta < 0, we are too slow but we cannot do anything about that
			if (started) {
				self ! GenerateData()
			}
		} else if (delta > 0) {
			// Too fast, get synced with expected time
			val waitFor = (delta * timerDuration).toLong

			if (started) {
				in(Duration(waitFor, TimeUnit.MILLISECONDS)) {
					self ! GenerateData()
				}
			}
		}
	}
}

abstract class Generator {
	def nextValue: Any
}

/**
 * Generate data from a normal distribution, with average mu and standard deviation sigma.
 * @param name The name of the field to generate
 * @param mu The average of the normal distribution
 * @param sigma The standard deviation of the normal distribution
 * @return A new double value sampled from the generator function
 *         with the given average and standard deviation.
 */
class NormalGenerator(format: String) extends Generator {
	private var mu: Double = _
	private var sigma: Double = _
	private var normal: GaussianGenerator = _

	// Generate data from normal distribution
	val regex = new Regex( """N\(([-+]?[0-9]*\.?[0-9]+),\s*([-+]?[0-9]*\.?[0-9]+)\)""", "mu", "sigma")

	regex.findFirstMatchIn(format) match {
		case Some(m) =>
			mu = m.group("mu").toDouble
			sigma = m.group("sigma").toDouble
			normal = new GaussianGenerator(mu, sigma, new MersenneTwisterRNG())
		case None => throw new IllegalArgumentException(format)
	}

	def nextValue: Any = {
		normal.nextValue()
	}
}

/**
 * Generate a new uniform random number.
 * @param formatString The format that looks like "U(100)" which means
 *                     that a uniform number between 0 and 100 should be
 *                     generated, with 0 inclusive and 100 exclusive.
 * @return A number sampled from this distribution.
 */
class UniformGenerator(format: String) extends Generator {
	private var min: Double = _
	private var max: Double = _
	private var uniform: ContinuousUniformGenerator = _

	// Generate data from uniform distribution
	val regex = new Regex( """U\(([-+]?[0-9]*\.?[0-9]+)\)""", "max")

	regex.findFirstMatchIn(format) match {
		case Some(m) =>
			val max = m.group("max").toDouble
			uniform = new ContinuousUniformGenerator(0, max, new MersenneTwisterRNG())
		case None => throw new IllegalArgumentException(format)
	}

	def nextValue: Any = {
		uniform.nextValue()
	}
}

/**
 * Choose a random value from an array of values.
 * @param formatString The array. It looks like "['a', 'b', 'c', 'd']".
 * @return A value picked from this array.
 */
class ArrayChoiceGenerator(format: String) extends Generator {
	private var items: List[String] = _
	private var array: DiscreteUniformGenerator = _

	if (!format.matches("""\[(,?('.*?'))+\]""")) {
		throw new IllegalArgumentException(format)
	}

	items = format.replace("[", "").replace("]", "").split(",").map(i => {
		i.replace("'", "").trim
	}).toList

	if (items.size == 0) {
		throw new IllegalArgumentException(format)
	}

	array = new DiscreteUniformGenerator(0, items.size - 1, new MersenneTwisterRNG())

	def nextValue: Any = {
		"\"" + items(array.nextValue()) + "\""
	}
}

/**
 * Generate always the same string, the input format string
 * @param format The format string that will be returned
 */
class ConstantGenerator(format: String) extends Generator {
	def nextValue: Any = {
		"\"" + format + "\""
	}
}