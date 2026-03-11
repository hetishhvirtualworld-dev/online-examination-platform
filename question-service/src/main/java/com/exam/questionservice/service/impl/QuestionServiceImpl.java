package com.exam.questionservice.service.impl;

import com.exam.questionservice.client.UserServiceFeignClient;
import com.exam.questionservice.client.dto.UserClientResponse;
import com.exam.questionservice.constant.QuestionStatus;
import com.exam.questionservice.domain.entity.Question;
import com.exam.questionservice.dto.request.CreateQuestionRequest;
import com.exam.questionservice.dto.response.QuestionResponse;
import com.exam.questionservice.exception.QuestionNotFoundException;
import com.exam.questionservice.mapper.QuestionMapper;
import com.exam.questionservice.repository.QuestionRepository;
import com.exam.questionservice.service.QuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionServiceImpl implements QuestionService {

    private final QuestionRepository questionRepository;
    
    private final UserServiceFeignClient userClient;

    @Override
    public QuestionResponse createQuestion(String facultyId, CreateQuestionRequest request) {
        log.info("Creating new question for subject: {}", request.getSubject());
        
        // Validate faculty exists
        UserClientResponse user = userClient.getUserById(Long.valueOf(facultyId));

        if (!"FACULTY".equals(user.getRole())) {
            throw new RuntimeException("Only faculty can create questions");
        }

        Question question = QuestionMapper.toEntity(request);
        Question saved = questionRepository.save(question);

        return QuestionMapper.toResponse(saved);
    }

    @Override
    public QuestionResponse getQuestionById(Long id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new QuestionNotFoundException("Question not found"));

        return QuestionMapper.toResponse(question);
    }

    @Override
    public List<QuestionResponse> getApprovedQuestionsBySubject(String subject) {
        return questionRepository
                .findBySubjectAndStatus(subject, QuestionStatus.APPROVED)
                .stream()
                .map(QuestionMapper::toResponse)
                .collect(Collectors.toList());
    }
}