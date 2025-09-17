package com.akiramenai.videobackend.model;

public enum CourseItemOperationErrors {
  CourseNotFound,
  ItemNotFound,
  InvalidRequest,
  AttemptingToModifyOthersCourse,
  FailedToSerializeJson,
  FailedToSaveToDb,
  FailedToSaveFile,
}
