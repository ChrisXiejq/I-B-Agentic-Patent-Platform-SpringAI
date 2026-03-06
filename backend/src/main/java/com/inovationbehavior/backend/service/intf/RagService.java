package com.inovationbehavior.backend.service.intf;

import org.springframework.stereotype.Service;

@Service
public interface RagService {
    String getRagAnswer(String userQuery, String context);
}
