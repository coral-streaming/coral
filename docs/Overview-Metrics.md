---
title: Metrics
layout: default
topic: Overview
---
<!--
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->

# Metrics

It is possible to send metrics to Graphite from Coral actors you create. To do this, use the trait Metrics. You now have metrics object available, which you can use
to create a metric and next use it. The library used by Coral is the [Metrics-Scala](https://github.com/erikvanoosten/metrics-scala) library.
See the [documentation](https://github.com/erikvanoosten/metrics-scala/blob/master/docs/Manual.md) of this library on how the metrics object works.

See the graphite section of the application.conf for the configuration. By default, logging to Graphite is disabled. At the moment, none of the provided Coral actors
 use the metrics.

#### Example
{% highlight scala %}
class MyActor extends CoralActor with Metrics with SimpleEmitTrigger {
    val requests = metrics.meter("requests")

    override def simpleEmitTrigger(json: JObject): Option[JValue] = {
        ...
        // Measure the number of requests.
        requests.mark()
        ...
    }
}
{% endhighlight %}