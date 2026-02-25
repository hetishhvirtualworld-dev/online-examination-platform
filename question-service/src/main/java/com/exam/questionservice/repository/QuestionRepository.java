package com.exam.questionservice.repository;

import com.exam.questionservice.domain.entity.Question;
import com.exam.questionservice.constant.QuestionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findBySubjectAndStatus(String subject, QuestionStatus status);
}