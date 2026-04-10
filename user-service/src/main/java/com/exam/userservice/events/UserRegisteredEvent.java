package com.exam.userservice.events;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class UserRegisteredEvent {

    private Long userId;
    private String fullName;
    private String email;
    private String role;
}
