package com.tellenn.artifacts.clients.requests

import com.fasterxml.jackson.annotation.JsonProperty

@Suppress("unused")
class ChangePasswordRequest(
    @param:JsonProperty("old_password") val oldPassword: String,
    @param:JsonProperty("new_password") val newPassword: String
)