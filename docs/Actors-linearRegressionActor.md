---
layout: default
title: linearRegressionActor
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

# LinearRegressionActor
The `LinearRegression` is a [Coral Actor](https://github.com/coral-streaming/coral/wiki/Coral-Actors) which performs prediction on the streaming data, based on the behavior of past observations.

## Creating a LinearRegressionActor
The creation JSON of the LinearRegression actor (see [Coral Actor](https://github.com/coral-streaming/coral/wiki/Coral-Actors)) has `"type": "LinearRegression"`.
The `params` value contains the following fields:

field  | type | required | description
:----- | :---- | :--- | :------------
`weights` | JObject | yes | An object containing the feature names and coefficients of the model.
`intercept` | Double | yes | The intercept of the linear regression model.

#### Example
{% highlight json %}
{
  "data": {
      "type": "actors",
      "attributes": {
          "type": "linearregression",
          "params": {
              "intercept": 3.972,
              "weights": {
                "salary": 0.47353,
                "tnxcount1month": 1.86766,
                "age": 4.52352
              }
          }
      }
  }
}
{% endhighlight %}

## Trigger
The `LinearRegressionActor` accepts JSON objects that contain the fields that were defined in the constructor.
An example of a JSON object that comes in is as follows:

{% highlight json %}
{
  "salary": 3000,
  "tnxcount1month": 25,
  "age": 30
}
{% endhighlight %}

Based on this input object, the LinearRegressionActor would calculate the following:

The score that is calculated is intercept + weightSalary * salary + weightTnxcount1month * tnxcount1month + weightAge * age, which is
3.972 + 0.47353 * 3000 + 1.86766 * 25 + 4.52352 * 30, which is 1606,9591.

## Emit
The `LinearRegressionActor` enriches the received trigger JSON with the predicted score and emits it. It looks like the following:

{% highlight json %}
{
   "salary": 3000,
   "tnxcount1month": 25,
   "age": 30,
   "score": 1606.9591
}
{% endhighlight %}

## State
The `LinearRegressionActor` does not keep any state.

## Collect
The `LinearRegressionActor` does not collect state from other actors.

## Timer
The `LinearRegressionActor` does not provide timer actions.
