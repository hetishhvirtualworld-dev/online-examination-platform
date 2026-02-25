package com.exam.questionservice.dto.request;

import lombok.Data;

@Data
public class CreateQuestionRequest {

    private String subject;
    private String chapter;
    private String questionText;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String correctAnswer;
}