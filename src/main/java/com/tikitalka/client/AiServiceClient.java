package com.tikitalka.client;

import com.tikitalka.dto.AiServiceRequest;
import com.tikitalka.dto.AiServiceResponse;

public interface AiServiceClient {
    AiServiceResponse call(AiServiceRequest request);
}
