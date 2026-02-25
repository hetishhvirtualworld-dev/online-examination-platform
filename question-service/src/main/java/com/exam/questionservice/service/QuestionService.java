package com.exam.questionservice.service;

import com.exam.questionservice.dto.request.CreateQuestionRequest;
import com.exam.questionservice.dto.response.QuestionResponse;

import java.util.List;

public interface QuestionService {

    QuestionResponse createQuestion(Long facultyId, CreateQuestionRequest request);

    QuestionResponse getQuestionById(Long id);

    List<QuestionResponse> getApprovedQuestionsBySubject(String subject);
}