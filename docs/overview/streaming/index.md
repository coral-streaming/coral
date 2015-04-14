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
# Coral Streaming Analytics

Coral's real time analytics data pipeline consists of loosely coupled [actors](/actors/overview/).
An actor is a share-nothing processing element which receive and emits messages via messages mailboxes. Data is transfered between actors has binary json,
and each actor interpret the relevant part of the message according to its given configuration.Each actor is functionally separate from its neighboring actors.
Events are transported asynchronously across a pipeline of loosely coupled actors. This provides a better scaling model with higher reliability and scalability.
The topology can be changed without restarting the cluster.