package com.akiramenai.videobackend.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {
  private String username;
  private String password;
  private String email;
  private String accountType;
}
