package io.coral.coralscript

import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestActorRef, TestProbe, ImplicitSender, TestKit}
import akka.util.Timeout
import io.coral.actors.CoralActorFactory
import io.coral.actors.Messages.Trigger
import io.coral.actors.transform.{CoralScriptActor, UnlistActor}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration._

class CoralScriptActorSpec (_system: ActorSystem) extends TestKit(_system)
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {
    def this() = this(ActorSystem("coralScriptActor"))

    override def afterAll() {
        TestKit.shutdownActorSystem(system)
    }

    implicit val timeout = Timeout(1.seconds)

    "a CoralScriptActor" should {
        "Properly parse script1 and act on it" in {
            val script1 = """event Transaction {
                    transactionId: Long,
                    accountId: Long,
                    amount: Float,
                    datetime: DateTime,
                    description: String
                }


                collect collectAge(accountId) {
                    from: db1Actor
                    with: "select age from customers where accountId = {accountId}"
                }

                feature avgAmountPerDay {
                    select avg(Person.transactions.amount)
                    from Person
                    group by day
                }

                action action1 = {
                    while (x < 10) {
                        println(x)
                    }

                    emit { "transactionId": Transaction.transactionId, "outlier": true }
                }

                condition condition1 = {
                    Transaction.amount > max(avgAmountPerDay)
                }

                trigger action1 on condition1
            """

            val probe = TestProbe()
            val constructorString =
                s"""{ "type": "coralscript", "params": { "script":
                   |${quoteStringForJson(script1)} }}""".stripMargin
            val constructor = parse(constructorString).asInstanceOf[JObject]
            val props: Props = CoralActorFactory.getProps(constructor).get
            val scriptActor = TestActorRef[CoralScriptActor](props)
            scriptActor.underlyingActor.emitTargets += probe.ref

            val transaction1 = parse("""{
                    "datatype": "transaction",
                    "accountId": 1234,
                    "amount": 54.20,
                    "datetime": "29-03-2015 18:41:23.582",
                    "description": "Esso"
                }""").asInstanceOf[JObject]
            val transaction2 = parse("""{
                    "datatype": "transaction",
                    "accountId": 5678,
                    "amount": 120.53,
                    "datetime": "29-03-2015 18:42:23.582",
                    "description": "Albert Heijn"
                }""").asInstanceOf[JObject]
            val balanceInfo1 = parse("""{
                    "datatype": "balance",
                    "accountId": 1234,
                    "amount": 2943.18,
                    "datetime": "27-03-2015 10:18:12.883"
                }""").asInstanceOf[JObject]
            val balanceInfo2 = parse("""{
                    "datatype": "balance",
                    "accountId": 1234,
                    "amount": 2943.18,
                    "datetime": "27-03-2015 10:18:12.883"
                }""").asInstanceOf[JObject]

            scriptActor ! Trigger(transaction1)
            probe.expectNoMsg()

            scriptActor ! Trigger(transaction2)
            probe.expectNoMsg()

            scriptActor ! Trigger(balanceInfo1)
            probe.expectNoMsg()

            scriptActor ! Trigger(balanceInfo2)

            val expected = parse("""{
                    "amount": Person.amount, "average": avgAmountPerDay
                }""").asInstanceOf[JObject]
        }

        "Properly parse a single action" in {
            val action1 = """action action1 = {
                while (x < 10) {
                    x + 1
                }}"""

            val probe = TestProbe()
            val constructorString =
                s"""{ "type": "coralscript", "params": { "script":
                   |${quoteStringForJson(action1)} }}""".stripMargin
            val constructor = parse(constructorString).asInstanceOf[JObject]
            val props: Props = CoralActorFactory.getProps(constructor).get
            val scriptActor = TestActorRef[CoralScriptActor](props)
        }
    }

    def quoteStringForJson(string: String): String = {
        if (string == null || string.length() == 0) {
            return "\"\""
        }

        var c: Char = 0
        val len: Int = string.length
        val sb = new StringBuilder(len + 4)
        var t: String = null

        sb.append('"')

        for (i <- 0 until len) {
            c = string.charAt(i)

            c match {
                case '\\' =>
                case '"' =>
                    sb.append('\\')
                    sb.append(c)
                case '/' =>
                    sb.append('\\')
                    sb.append(c)
                case '\b' =>
                    sb.append("\\b")
                case '\t' =>
                    sb.append("\\t")
                case '\n' =>
                    sb.append("\\n")
                case '\f' =>
                    sb.append("\\f")
                case '\r' =>
                    sb.append("\\r")
                case _ =>
                    if (c < ' ') {
                        t = "000" + Integer.toHexString(c)
                        sb.append("\\u" + t.substring(t.length() - 4))
                    } else {
                        sb.append(c)
                    }
            }
        }

        sb.append('"')
        sb.toString()
    }
}