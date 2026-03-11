package com.exam.questionservice.controller;

import com.exam.questionservice.dto.request.CreateQuestionRequest;
import com.exam.questionservice.dto.response.QuestionResponse;
import com.exam.questionservice.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    @PostMapping
    public ResponseEntity<QuestionResponse> createQuestion(@RequestHeader("X-User-Id") String facultyId, @RequestBody CreateQuestionRequest request) {
        return ResponseEntity.ok(questionService.createQuestion(facultyId, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<QuestionResponse> getQuestion(@PathVariable Long id) {
        return ResponseEntity.ok(questionService.getQuestionById(id));
    }

    @GetMapping("/subject/{subject}")
    public ResponseEntity<List<QuestionResponse>> getApprovedBySubject(@PathVariable String subject) {
        return ResponseEntity.ok(questionService.getApprovedQuestionsBySubject(subject));
    }
}