package com.examplatform.auth.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserRegisteredEvent {

	private Long userId;
	private String fullName;
	private String email;
	private String role;
}
