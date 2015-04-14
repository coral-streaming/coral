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
# LookupActor
The `LookupActor` is a [Coral Actor](/actors/overview/) that can enrich data via the lookup table provided in its constructor.

## Creating a LookupActor
The creation JSON of the LookupActor actor (see [Coral Actor](/actors/overview/)) has `"type": "lookup"`.
The `params` value contains the following fields:

field  | type | required | description
:----- | :---- | :--- | :------------
`key` | string | yes | name of the key field
`lookup` | JSON object | yes | the lookup table
`function` | string | yes | "enrich", "filter" or "check"

The `function` field in the constructor defines the behavior of the `LookupActor`.

#### Example
```json
{
  "type": "lookup",
  "params": {
    "key": "city",
    "lookup": {
      "amsterdam": { "country": "netherlands", "population": 800000 },
      "vancouver": { "country": "canada", "population": 600000 }
    },
    "function": "enrich"
  }
}
```

This `LookUpActor` will now wait for any incoming trigger messages and will look at the "city" field. 
It will merge the "country" and "population" fields into the output message since "function" is set to "enrich".

If this is the input:
```json
{
  "city": "amsterdam",
  "otherdata": "irrelevant",
  "somevalue": 10
}
```

Then `enrich` will emit this output:
```json
{
   "city": "amsterdam",
   "otherdata": "irrelevant",
   "somevalue": 10,
   "country": "netherlands",
   "population": 800000
}
```

If the function is set to `filter`, the output is

```json
{
  "city": "amsterdam",
  "otherdata": "irrelevant",
  "somevalue": 10
}
```

but only if the "city" key is found in the lookup map. If it is not found, the input object is not emitted.

In the case of `check`, the output is the looked up object if it exists, else, nothing is emitted:
```json
{
   "country": "netherlands",
   "population": 800000
}
```

## Trigger
The `LookupActor` accepts any JSON as trigger. If a key-field is encountered the fields corresponding to the value in that field will be used to look up additional data (from the lookup table).

## Emit
The `function` of the `LookupActor` determines what is emitted.

##### enrich
The function `enrich` always emits the originig JSON. If lookup values are found these are added to the original JSON.

##### filter
The function `filter` emits the unenriched, original JSON when it is found in the lookup table. If it is not found, nothing is emitted.

##### check
The function `check` emits the lookup value when it is found, otherwise, nothing is emitted.

## State
The `LookupActor`does not keep state.

## Collect
The `LookupActor` does not collect any data from other actors.

## Timer
The `LookupActor` does not provide timer actions.