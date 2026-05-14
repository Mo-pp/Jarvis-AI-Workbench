package com.msz.resume.ai.integrations.openviking.core.exception;

/**
 * OpenViking 客户端异常。
 */
public class OpenVikingClientException extends RuntimeException {

    public OpenVikingClientException(String message) {
        super(message);
    }

    public OpenVikingClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
