package com.akiramenai.videobackend.model;

public record CleanedQuiz(
    String itemId,
    String courseId,
    String question,
    String o1,
    String o2,
    String o3,
    String o4,
    int correctOption,
    boolean isCompleted
) {
  public CleanedQuiz(Quiz q, boolean isCompleted) {
    this(
        q.getItemId(),
        q.getCourseId().toString(),
        q.getQuestion(),
        q.getO1(),
        q.getO2(),
        q.getO3(),
        q.getO4(),
        q.getCorrectOption(),
        isCompleted
    );
  }
}
