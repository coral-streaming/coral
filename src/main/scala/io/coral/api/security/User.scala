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

package io.coral.api.security

import java.util.UUID
import com.github.t3hnar.bcrypt._
import org.mindrot.jbcrypt.BCrypt

/**
 * Represents a user in the platform.
 */
case class User(// The unique identifier of the user
			    id: UUID,
			    // The full, friendly name of the user
				fullName: String,
			    // The department of the user, if any
			   	department: Option[String],
			    // The email address of the user
			   	email: String,
			    // The uniquely identifying name on the platform
			   	uniqueName: String,
			    // The mobile phone number of the user
			   	mobilePhone: Option[String],
			    // The *hashed* password of the user
				hashedPassword: String,
			    // Timestamp when this user is created
			   	createdOn: Long,
			    // Timestamp when the user last logged in
				lastLogin: Option[Long]) {
	def withPassword(password: String) = {
		copy(hashedPassword = password.bcrypt(generateSalt))
	}

	def passwordMatches(password: String): Boolean = {
		BCrypt.checkpw(password, hashedPassword)
	}
}