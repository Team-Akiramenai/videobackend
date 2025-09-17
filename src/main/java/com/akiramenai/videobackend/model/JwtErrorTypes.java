package com.akiramenai.videobackend.model;

public enum JwtErrorTypes {
  JwtExpiredException,
  JwtSignatureException,
  JwtMalformedException,
  JwtUnsupportedException,
  JwtIllegalArgumentException,
}
