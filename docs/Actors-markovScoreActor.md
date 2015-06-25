---
layout: default
title: markovScoreActor
topic: Actors
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

# MarkovScoreActor
The `MarkovScore` is a [Coral Actor](https://github.com/coral-streaming/coral/wiki/Coral-Actors) which provides Markov score on click streaming path.

## Creating a MarkovScoreActor
The creation JSON of the MarkovScore actor (see [Coral Actor](https://github.com/coral-streaming/coral/wiki/Coral-Actors)) has `"type": "markovscore"`.
The `params` value contains the following fields:

field  | type | required | description
:----- | :---- | :--- | :------------
`transitionProbs` | JArray | yes | An array containing the state transitions with the corresponding probabilities.

#### Example
{% highlight json %}
{
  "data": {
      "type": "actors",
      "attributes": {
          "type": "markovscore",
          "params": {
              "transitionProbs":[
                  {
                    "source": "s009",
                    "destination": "s010",
                    "prob": 0.1
                  }, {
                    "source": "s009",
                    "destination": "b002",
                    "prob": 0.2
                  }, {
                   "source": "s009",
                   "destination": "b001",
                   "prob": 0.4
                  }
              ]
          }
      }
  }
}
{% endhighlight %}

## Trigger
The `MarkovScoreActor` accepts JSON objects that contain list of click path states.
An example of a JSON object that comes in is as follows:

{% highlight json %}
{
  "transitions":["s001", "s002", "s003"]
}
{% endhighlight %}

Based on this input object, the MarkovScoreActor would calculate the click path score.


## Emit
The `MarkovScoreActor` emits the Markov score. It looks like the following:

{% highlight json %}
{
   "score": 0.4
}
{% endhighlight %}

## State
The `MarkovScoreActor` does not keep any state.

## Collect
The `MarkovScoreActor` does not collect state from other actors.
