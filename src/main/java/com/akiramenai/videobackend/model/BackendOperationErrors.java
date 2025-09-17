package com.akiramenai.videobackend.model;

public enum BackendOperationErrors {
  CourseNotFound,
  ItemNotFound,
  InvalidRequest,
  AttemptingToModifyOthersItem,
  FailedToSerializeJson,
  FailedToSaveToDb,
  FailedToSaveFile,
}
