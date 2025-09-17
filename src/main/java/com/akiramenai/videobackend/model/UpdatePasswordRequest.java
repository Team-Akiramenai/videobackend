package com.akiramenai.videobackend.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdatePasswordRequest {
  private String oldPassword;
  private String newPassword;
}
