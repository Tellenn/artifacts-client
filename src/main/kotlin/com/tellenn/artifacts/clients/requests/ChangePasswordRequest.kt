package com.tellenn.artifacts.clients.requests

import com.fasterxml.jackson.annotation.JsonProperty

class ChangePasswordRequest(
    @JsonProperty("old_password") val oldPassword: String,
    @JsonProperty("new_password") val newPassword: String
)