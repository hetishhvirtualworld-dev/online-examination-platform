package com.exam.questionservice.client;

import com.exam.questionservice.client.dto.UserClientResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "user-service",
        url = "${services.user-service.url}"
)
public interface UserServiceFeignClient {

    @GetMapping("/api/v1/users/{id}")
    UserClientResponse getUserById(@PathVariable Long id);
}