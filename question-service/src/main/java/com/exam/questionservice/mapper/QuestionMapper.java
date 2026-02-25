package com.exam.questionservice.mapper;

import com.exam.questionservice.domain.entity.Question;
import com.exam.questionservice.constant.QuestionStatus;
import com.exam.questionservice.dto.request.CreateQuestionRequest;
import com.exam.questionservice.dto.response.QuestionResponse;

public class QuestionMapper {

    public static Question toEntity(CreateQuestionRequest request) {
        return Question.builder()
                .subject(request.getSubject())
                .chapter(request.getChapter())
                .questionText(request.getQuestionText())
                .optionA(request.getOptionA())
                .optionB(request.getOptionB())
                .optionC(request.getOptionC())
                .optionD(request.getOptionD())
                .correctAnswer(request.getCorrectAnswer())
                .status(QuestionStatus.PENDING)
                .build();
    }

    public static QuestionResponse toResponse(Question question) {
        return QuestionResponse.builder()
                .id(question.getId())
                .subject(question.getSubject())
                .chapter(question.getChapter())
                .questionText(question.getQuestionText())
                .optionA(question.getOptionA())
                .optionB(question.getOptionB())
                .optionC(question.getOptionC())
                .optionD(question.getOptionD())
                .build();
    }
}