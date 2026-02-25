package com.exam.questionservice.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuestionResponse {

    private Long id;
    private String subject;
    private String chapter;
    private String questionText;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
}