---
title: Why Coral?
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

# Why Coral

Use just a single API to let you ingest data from multiple data sources. Leverage millions of events created daily across numerous data sources, both internally and (if required) externally. Fast, secure and robust. Over the past years batch-oriented data platforms like Hadoop have been used successfully for user behavior analytics. More recently, we have newer set of use cases that demand near real-time (within seconds) collection and processing of vast amount of events in order to derive actionable insights and generate signals for immediate actions. Example of use cases include:

* Marketing and advertising
* Fraud and bot detection
* Infrastructure monitoring
* Personalization

The coral platform is aiming to fulfil the following goals:

* Scalability - Scale to millions of events per second
* Latency - milli-seconds event processing engine
* Availability - No cluster downtime during software upgrade, stream processing rules and topology changes
* Flexibility - Easy to define and modify event dataflows and multi-tenancy support
* Productivity - Support for Complex event processing (CEP) and 
* Connectivity - Support for Cassandra, Elastic Search, Kafka and Spark 
* Cloud Deployable - Can distribute node across data centers on standard cloud infrastructure

Given our unique set of requirements we decided to develop our own distributed CEP framework. Coral CEP framework provides a Scala based CEP framework and tooling to build, deploy and management CEP applications in a cloud environment. 