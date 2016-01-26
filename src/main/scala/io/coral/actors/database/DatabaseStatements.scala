/*
 * Copyright 2016 Coral realtime streaming analytics (http://coral-streaming.github.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.coral.actors.database

import io.coral.api.CoralConfig

object DatabaseStatements {
	def createUserTable(config: CoralConfig) =
		s"""create table if not exists ${config.coral.cassandra.keyspace}.${config.coral.cassandra.userTable}
			| (id uuid,
			| fullname text,
			| department text,
			| email text,
			| uniquename text,
			| mobilephone text,
			| hashedpassword text,
			| createdon timestamp,
			| lastlogin timestamp,
			| primary key (uniquename));""".stripMargin

	def createAuthorizationTable(config: CoralConfig) =
		s"""create table if not exists ${config.coral.cassandra.keyspace}.${config.coral.cassandra.authorizeTable}
			| (user uuid,
			| id uuid,
			| runtime uuid,
			| method text,
			| uri text,
			| allowed boolean,
			| primary key ((user), id));""".stripMargin

	def createRuntimeTable(config: CoralConfig) =
		s"""create table if not exists ${config.coral.cassandra.keyspace}.${config.coral.cassandra.runtimeTable}
			| (id uuid,
			| owner uuid,
			| name text,
			| adminpath text,
			| status int,
			| projectid uuid,
			| jsondef text,
			| startedon timestamp,
			| primary key (id));""".stripMargin

	def initializeDatabase(config: CoralConfig): Array[String] = {
		val stmts = Array(
			s"""{ "query": "use ${config.coral.cassandra.keyspace};" } """,
			s"""{ "query": "${createUserTable(config)}" } """,
			s"""{ "query": "${createAuthorizationTable(config)}" } """,
			s"""{ "query": "${createRuntimeTable(config)}" } """)

		if (config.coral.cassandra.keyspaceAutoCreate) {
			Array(s"""{ "query": "create keyspace if not exists ${config.coral.cassandra.keyspace} """ +
				s"""with replication = { 'class': 'SimpleStrategy', 'replication_factor': 1 };" } """) ++ stmts
		} else {
			stmts
		}
	}
}
