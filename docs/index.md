---
layout: default
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

## Overview

Coral is a real-time analytics and data science platform. It transforms streaming events and extract patterns from data via RESTful APIs.
Built on Spray, Akka and Spark. It can be used to collect and process events in realtime to provide key insights and enable systems to act in milliseconds.
Coral provides a way to define event-driven dataflows by means of RESTful webapi calls. A library of ready-made processing elements (aka coral actors)
is available for basic functions such as filtering, expressions, database accesses, and statistical operators. Event processing can be defined as a set
of run-time coral actors. Coral scales to millions of events per second with high availability.