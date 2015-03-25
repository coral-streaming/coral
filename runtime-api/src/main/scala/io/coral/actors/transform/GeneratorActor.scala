package io.coral.actors.transform

import java.util.Random
import akka.actor.{PoisonPill, Props}
import io.coral.actors.Messages.TimeoutEvent
import io.coral.actors.{TimerContinue, CoralActor}
import org.json4s.JValue
import org.json4s.JsonAST.JValue
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.uncommons.maths.random.{DiscreteUniformGenerator, ContinuousUniformGenerator, GaussianGenerator}
import scala.concurrent.duration._
import scala.util.matching.Regex
import org.uncommons.maths._

import scala.util.matching.Regex.MatchIterator

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
 *     "format": {
 *           // Normal distribution with mu 100 and sigma 10
 *           "field1": "N(100, 10)",
 *           // choose randomly from a list of values
 *           "field2": "['a', 'b', 'c']",
 *           // uniform distribution with max = 100
 *           "field3": "U(100)"
 *     }, "timer": {
 *        // per second
 *        "rate": "10",
 *        // total number of items to generate
 *        "times": "100",
 *        // initial delay in seconds
 *        "delay": "10"
 *     }
 * }
 */
object GeneratorActor {
    implicit val formats = org.json4s.DefaultFormats

    def getParams(json: JValue) = {
        for {
        // The structure of the object to emit
            format <- (json \ "format").extractOpt[JObject]
            rate <- (json \ "timer" \ "rate").extractOpt[Int]
            times <- (json \ "timer" \ "times").extractOpt[Int]
            delay <- (json \ "timer" \ "delay").extractOpt[Int]
        } yield {
            (format, rate, times, delay)
        }
    }

    def apply(json: JValue): Option[Props] = {
        getParams(json).map(_ => Props(classOf[GeneratorActor], json))
    }
}

class GeneratorActor(json: JObject) extends CoralActor {
    def jsonDef = json

    val (format, rate, times, delay) = GeneratorActor.getParams(json).get

    // This is always true for the generator actor
    override def timerMode = TimerContinue

    // time to wait before ticking again in milliseconds
    override def timerDuration = ((1.0f / rate.toFloat) * 1000).toLong

    var startTime: Long = _

    override def preStart() {
        startTime = System.currentTimeMillis
        self ! TimeoutEvent
    }

    def state = Map.empty

    def trigger = noProcess

    def emit = doNotEmit

    var count = 0

    def timer = {
        val currentTime = System.currentTimeMillis

        // If this is true, we are not in the initial delay period any more
        if ((currentTime - startTime) >= (delay * 1000)) {
            count += 1

            // Reached the maximum output number
            if (count > times) {
                self ! PoisonPill
                JNothing
            } else {
                generateData(format)
            }
        } else {
            JNothing
        }
    }

    /**
     * Generate data based on a JObject format definition.
     * The fields are maintained but replaced with values from
     * a uniform, normal or discrete choice distribution.
     * @param format The format JSON object to process
     * @return A JSON object in the same format but with values
     *         filled in.
     */
    def generateData(format: JObject): JValue = {
        try {
            var result = JObject()

            format.obj.foreach(elem => {
                val name: String = elem._1

                // In the case of a nested JSON object, process
                // nested JObject recursively
                elem._2 match {
                    case JObject(items) =>
                        result = result ~ (name -> generateData(items))
                    case _ =>
                        val JString(formatString) = elem._2
                        val value: JValue = getNextValue(formatString)

                        if (value == JNothing) {
                            throw new IllegalArgumentException("formatString")
                        }

                        val pair = (name -> value)
                        result = result merge JObject(pair)
                }
            })

            result
        } catch {
            case e: Exception =>
                log.error("Error in creating data from generator functions")
                JNothing
        }
    }

    /**
     * Gets the next value for a given format string.
     * @param formatString The format string to analyze.
     * @return A next value, either a string, a double chosen
     *         from a uniform distribution or a double chosen
     *         from a normal (gaussian) distribution.
     */
    def getNextValue(formatString: String): JValue = {
        formatString.charAt(0) match {
            case 'N' => // N(mu, sigma)
                // Generate data from normal distribution
                val regex = new Regex("""N\(([-+]?[0-9]*\.?[0-9]+),\s*([-+]?[0-9]*\.?[0-9]+)\)""", "mu", "sigma")

                regex.findFirstMatchIn(formatString) match {
                    case Some(m) =>
                        val mu = m.group("mu").toDouble
                        val sigma = m.group("sigma").toDouble
                        JDouble(new GaussianGenerator(mu, sigma, new Random()).nextValue())
                    case None => JNothing
                }
            case 'U' => // U(max)
                // Generate data from uniform distribution
                val regex = new Regex( """U\(([-+]?[0-9]*\.?[0-9]+)\)""", "max")

                regex.findFirstMatchIn(formatString) match {
                    case Some(m) =>
                        val max = m.group("max").toDouble
                        JDouble(new ContinuousUniformGenerator(0, max, new Random()).nextValue())
                    case None => JNothing
                }
            case '[' => // ["e1", "e2", ...]
                // Choose item from list randomly
                val items = if (formatString.contains(",")) {
                    {
                        for {
                            i <- formatString.replace("[", "").replace("]", "").split(",")
                            i2 <- i.replace("'", "").trim()
                            if (i2 != "")
                        } yield {
                            i2
                        }
                    }.toList
                } else {
                    List(formatString.replace("['", "").replace("']", "").trim())
                }

                val index = new DiscreteUniformGenerator(0, items.size - 1, new Random()).nextValue()
                JString(items(index))
            case _ =>
                // Generator function not recognized
                throw new IllegalArgumentException("format string")
        }
    }
}