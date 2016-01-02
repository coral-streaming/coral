---
layout: default
title: Prerequisites
topic: Getting started
order: 1
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

## Prerequisites

The following prerequisites exist to run Coral: 

- **API knowledge**  
Basic knowledge of RESTful interfaces and JSON. Coral is controlled through a RESTful HTTP interface. At this moment, there is no visual interface yet, so the application can only be controlled through its HTTP API.
- **Cassandra**  
A running Cassandra instance. To download the latest version of Cassandra, visit [http://cassandra.apache.org](http://cassandra.apache.org). We have not tested running Coral with older versions of Cassandra. For this reason, the latest version is recommended.
- **Kafka**  
A running Kafka instance. To download the latest version of Kafka, visit the [website](http://kafka.apache.org). We have not tested running Coral with older versions of Kafka. For this reason, the latest version is recommended.
- **HTTP client**  
An HTTP client. There are a lot of different ways to issue commands to Coral, all of which use HTTP. A convenient way to send API calls to Coral is with [Postman](http://www.getpostman.com), a browser plugin for [Google Chrome](https://www.google.com/chrome). It is also possible to use `curl`, a bash command line tool, to enter the commands. It is also possible to write a [Python](https://www.python.org) script to enter the commands. A python or bash script has the advantage that combinations of commands can be stored and later executed. So pick your favorite HTTP client!

If you are compiling from source and you want to obtain the latest version from GitHub, the following additional prerequisites exist:

- **Git**  
To obtain the latest version of the repository from GitHub, download Git [here](https://git-scm.com). It might already be installed on your machine, however. To find out, type `git --version` in a command prompt.
- **Scala**  
Scala 2.11.6. To download Scala, visit the [Scala website](http://www.scala-lang.org). It may already be preinstalled on your machine. To find out, type `scala -version`. We have not tested compiling and running Coral with earlier versions of Scala. For this reason, the latest version is recommended.
- **Maven**  
Coral uses Maven as its build system. It might already be installed on your machine. To find out, type `mvn --version` in a command prompt. If not installed, download it [here](http://maven.apache.org).