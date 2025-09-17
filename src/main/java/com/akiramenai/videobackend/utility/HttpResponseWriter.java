package com.akiramenai.videobackend.utility;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import com.akiramenai.videobackend.model.CourseItemOperationErrors;
import com.akiramenai.videobackend.model.ResultOrError;

@Slf4j
public class HttpResponseWriter {
  public void handleDifferentResponses(
      HttpServletResponse response,
      ResultOrError<String, CourseItemOperationErrors> result,
      HttpStatus onSuccessHttpStatus
  ) {
    switch (result.errorType()) {
      case InvalidRequest, AttemptingToModifyOthersCourse -> {
        writeFailedResponse(response, result.errorMessage(), HttpStatus.BAD_REQUEST);
        return;
      }
      case CourseNotFound, ItemNotFound -> {
        writeFailedResponse(response, result.errorMessage(), HttpStatus.NOT_FOUND);
        return;
      }
      case FailedToSaveToDb, FailedToSaveFile, FailedToSerializeJson -> {
        writeFailedResponse(response, result.errorMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        return;
      }
      case null -> {
        writeOkResponse(response, result.result(), onSuccessHttpStatus);
        return;
      }
    }
  }

  public void writeOkResponse(HttpServletResponse response, String respJson, HttpStatus httpStatusCode) {
    response.setContentType("application/json");
    response.setContentLength(respJson.getBytes().length);
    response.setStatus(httpStatusCode.value());

    try {
      response.getOutputStream().write(respJson.getBytes());
      response.getOutputStream().flush();
    } catch (Exception e) {
      log.error("Failed to write response. Reason: ", e);
    }
  }

  public void writeFailedResponse(HttpServletResponse response, String reason, HttpStatus httpStatusCode) {
    response.setContentType("application/text");
    response.setContentLength(reason.getBytes().length);
    response.setStatus(httpStatusCode.value());

    try {
      response.getOutputStream().write(reason.getBytes());
      response.getOutputStream().flush();
    } catch (Exception e) {
      log.error("Failed to write response. Reason: ", e);
    }
  }
}
