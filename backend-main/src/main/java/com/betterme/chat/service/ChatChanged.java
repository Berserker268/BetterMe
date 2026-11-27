package com.betterme.chat.service;

import java.util.Set;

public record ChatChanged(Set<Long> recipients, Long conversationId) {}
