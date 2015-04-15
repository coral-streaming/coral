---
title: Architecture
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

## Architecture

Here are some of the processing we perform in our real-time analytics pipeline:

* Enrichment - Decorate events with additional attributes. For example, we can add Geo location information to user interaction events based on the IP address range
* Filtering and Mutation - Filter out irrelevant attributes, events or transform content of a event
* Aggregation - Count number of events or add up metrics along a set of dimensions over a time window
* Stateful Processing - Group multiple events into one or generate a new event based on a sequence of events and processing rules. 

Coral dataflows can be integrated with different systems. For example, summarized events can be sent to a persistent metric stores to support ad-hoc queries. Events can also be sent to some form of visualization dashboard for real-time reportings or backend systems that can react to event signals.