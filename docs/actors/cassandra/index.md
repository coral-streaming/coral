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
# CassandraActor
The `CassandraActor` is a [Coral Actor](/actors/overview/) that enables communication with a Cassandra database. The actor receives queries in the trigger field, and emits data through its emit function. All valid CQL queries can be executed, including creating keyspaces and inserting, deleting and updating values.

## Creating a CassandraActor
The creation JSON of the CassandraActor (see [Coral Actor](/actors/overview/)) has `"type": "cassandra"`.
The `params` value is a JSON with a single field:

field  | type | required | description
:----- | :---- | :--- | :------------
`seeds` | List[String] | yes| A JSON list of the IP addresses of seed nodes
`keyspace` | String | yes| The name of the keyspace to connect to initially

#### Creation example
{% highlight json %}
{
  "type": "cassandra",
  "seeds": ["10.0.0.1", "10.0.0.2"],
  "keyspace": "keyspacename"
}
{% endhighlight %}
This will create a CassandraActor connecting to any of the nodes 10.0.0.1 or 10.0.0.2 to keyspace "keyspacename".

## Trigger
The `CassandraActor` only executes CQL statements on request through the shunt procedure (a single trigger-emit redirected to a requesting actor).

## Emit
The `CassandraActor` emits a JSON object which format depends on the type of query and the result of the query.
When the query is 

## State
The `CassandraActor` returns its state as follows:

field |type| description
:--- | :--- | :---
`connected` | boolean | Whether there is still an active connection to the Cassandra database at the time of collecting the state.
`keyspace` | String | The name of the keyspace the actor is currently connected to.
`schema` | Array | A JSON representation of the schema of the keyspace currently connected to.

An example of a schema is as follows:
{% highlight json %}
{
  "connected": true,
  "keyspace": "keyspacename",
  "schema": [{
    "table1": [
     { "column1": "text" },
     { "column2": "int" },
     { "column3": "float" }], 
    "table2": [
     { "col1": "float" },
     { "col2": "int" }]
  }]
}
{% endhighlight %}
