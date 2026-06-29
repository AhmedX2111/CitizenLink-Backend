package com.ntg.CitizenLink.dto.agent.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class HandlerResponse {

    private UUID id;
    private String displayName;
    private String email;
}
